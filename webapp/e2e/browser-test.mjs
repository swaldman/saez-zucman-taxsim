// End-to-end browser test for the demo site.
//
// Usage: node webapp/e2e/browser-test.mjs <siteDir> [standaloneHtml] [--screenshots <dir>]
// (normally via `./mill webapp.browserTest`).
//
// Serves <siteDir> on a free local port, drives headless Chrome over the DevTools protocol,
// and checks the interactions that screenshots can't: sliders, the bracket editor, presets,
// shareable links, hover tooltips, and the absence of console errors. If a standalone HTML
// file is given, it is also opened from disk and checked.
// Requires Node 22+ (built-in WebSocket) and Chrome/Chromium (set CHROME to override the path).

import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { extname, join, resolve, sep } from "node:path";
import { pathToFileURL } from "node:url";

const args = process.argv.slice(2);
const shotsIdx = args.indexOf("--screenshots");
const shotsDir = shotsIdx >= 0 ? resolve(args[shotsIdx + 1]) : null;
const positional = shotsIdx >= 0 ? args.filter((_, i) => i !== shotsIdx && i !== shotsIdx + 1) : args;
const [siteDir, standalone] = positional.map((p) => resolve(p));
if (!siteDir || !existsSync(join(siteDir, "index.html"))) {
  console.error("usage: browser-test.mjs <siteDir> [standaloneHtml] [--screenshots <dir>]");
  process.exit(2);
}
if (shotsDir) mkdirSync(shotsDir, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---- Static file server
const types = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".map": "application/json" };
const server = createServer((req, res) => {
  const path = decodeURIComponent(new URL(req.url, "http://x").pathname);
  const file = resolve(siteDir, "." + (path === "/" ? "/index.html" : path));
  if (!file.startsWith(siteDir + sep) || !existsSync(file) || !statSync(file).isFile()) {
    res.writeHead(404); res.end(); return;
  }
  res.writeHead(200, { "content-type": types[extname(file)] ?? "application/octet-stream", "cache-control": "no-store" });
  res.end(readFileSync(file));
});
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const BASE = `http://127.0.0.1:${server.address().port}/`;

// ---- Chrome
const candidates = [
  process.env.CHROME,
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/Applications/Chromium.app/Contents/MacOS/Chromium",
  "/usr/bin/google-chrome", "/usr/bin/google-chrome-stable", "/usr/bin/chromium", "/usr/bin/chromium-browser",
].filter(Boolean);
const chromePath = candidates.find((p) => existsSync(p));
if (!chromePath) { console.error("Chrome not found; set CHROME=/path/to/chrome"); process.exit(2); }
const profile = mkdtempSync(join(tmpdir(), "taxsim-e2e-"));
const chrome = spawn(chromePath, ["--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
  "--remote-debugging-port=0", `--user-data-dir=${profile}`, "--window-size=1280,1600", "about:blank"], { stdio: "ignore" });

async function devtoolsUrl() {
  const f = join(profile, "DevToolsActivePort");
  for (let i = 0; i < 100; i++) {
    if (existsSync(f)) {
      const [port, path] = readFileSync(f, "utf8").trim().split("\n");
      if (port && path) return `ws://127.0.0.1:${port}${path}`;
    }
    await sleep(100);
  }
  throw new Error("Chrome did not start (no DevToolsActivePort)");
}

const results = [];
let ws;
function cleanup() {
  try { ws?.close(); } catch {}
  chrome.kill();
  server.close();
  try { rmSync(profile, { recursive: true, force: true }); } catch {}
}

try {
  ws = new WebSocket(await devtoolsUrl());
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
  let id = 0; const pending = new Map(); const events = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); } else events.push(msg);
  };
  const send = (method, params = {}, sessionId) => new Promise((resolveMsg) => {
    const i = ++id; pending.set(i, resolveMsg); ws.send(JSON.stringify({ id: i, method, params, sessionId }));
  });
  const { result: { targetId } } = await send("Target.createTarget", { url: "about:blank" });
  const { result: { sessionId } } = await send("Target.attachToTarget", { targetId, flatten: true });
  const S = (m, p) => send(m, p, sessionId);
  await S("Runtime.enable"); await S("Page.enable");
  await S("Emulation.setDeviceMetricsOverride", { width: 1280, height: 1600, deviceScaleFactor: 1, mobile: false });

  const evalJs = async (expr) => {
    const r = await S("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true });
    if (r.result.exceptionDetails) throw new Error(r.result.exceptionDetails.exception?.description ?? JSON.stringify(r.result.exceptionDetails));
    return r.result.result.value;
  };
  // Wait until the app has rendered `selector` (rather than sleeping a fixed time).
  const waitFor = async (selector, timeoutMs = 10000) => {
    const t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) {
      if (await evalJs(`!!document.querySelector(${JSON.stringify(selector)})`)) return;
      await sleep(100);
    }
    throw new Error(`timed out waiting for ${selector}`);
  };
  const go = async (url, selector) => {
    await S("Page.navigate", { url });
    await sleep(300);
    await waitFor(selector);
    await sleep(200);
  };
  const settle = () => sleep(250);
  const shot = async (name) => {
    if (!shotsDir) return;
    const r = await S("Page.captureScreenshot", { format: "png" });
    writeFileSync(join(shotsDir, `${name}.png`), Buffer.from(r.result.data, "base64"));
  };
  const stats = () => evalJs(`[...document.querySelectorAll('.stat .value')].map(e => e.textContent)`);
  const check = (name, ok, detail) => results.push({ name, ok: !!ok, detail });
  const step = async (name, fn) => {
    try { await fn(); } catch (e) { check(name, false, String(e).slice(0, 300)); }
  };

  // Helpers injected into the page: set an input's value and fire events, as a user's edit would.
  const helpers = `
    window.__set = (el, v, ev) => {
      Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value').set.call(el, v);
      el.dispatchEvent(new Event(ev || 'input', { bubbles: true }));
    };
    window.__brackets = (label) => [...document.querySelectorAll('.brackets')]
      .find(b => b.querySelector('.lever-head').textContent.includes(label));
    window.__rows = (label) => [...__brackets(label).querySelectorAll('.row:not(.hdr)')];
  `;

  // ---- Simulator
  await go(BASE + "#/simulator", ".stat .value");
  await evalJs(helpers);
  await step("simulator: initial revenue is zero", async () => {
    const s = await stats(); check("simulator: initial revenue is zero", s[0] === "0.00%", s);
  });
  await step("simulator: a 5% VAT raises revenue", async () => {
    await evalJs(`__set(document.getElementById('lv-vat.rate'), '0.05')`); await settle();
    const s = await stats(); check("simulator: a 5% VAT raises revenue", /^\+\d/.test(s[0]) && s[0] !== "+0.00%", s);
    const hash = await evalJs("decodeURIComponent(location.hash)");
    check("simulator: the URL carries the plan", hash.includes('"vat.rate":0.05'), hash);
    await evalJs(`__set(document.getElementById('lv-vat.rate'), '0')`); await settle();
  });
  let bracketRevenue = null;
  let sharedHash = null;
  await step("brackets: add, type a threshold, set a rate", async () => {
    await evalJs(`[...__brackets('Income tax brackets').querySelectorAll('button')].find(b => b.textContent.includes('Add bracket')).click()`);
    await settle();
    check("brackets: 'Add bracket' adds a row", (await evalJs(`__rows('Income tax brackets').length`)) === 13);
    await evalJs(`{ const rows = __rows('Income tax brackets'); const t = rows[rows.length - 1].querySelector('input[type=text]');
      __set(t, '3m', 'input'); __set(t, '3m', 'change'); }`);
    await settle();
    const ts = await evalJs(`__rows('Income tax brackets').map(r => r.querySelector('input[type=text]').value)`);
    check("brackets: a typed threshold ($3m) is kept and sorted into place",
      ts.join(",") === "$0,$77k,$165k,$315k,$400k,$600k,$1m,$2.5m,$3m,$5m,$10m,$25m,$50m", ts);
    await evalJs(`__set(__rows('Income tax brackets').find(r => r.querySelector('input[type=text]').value === '$3m')
      .querySelector('input[type=range]'), '0.6')`);
    await settle();
    const s = await stats();
    bracketRevenue = s[0];
    check("brackets: a 60% rate above $3m raises revenue", /^\+\d/.test(s[0]) && s[0] !== "+0.00%", s);
    await evalJs(`{ const t = __rows('Income tax brackets')[3].querySelector('input[type=text]'); __set(t, 'abc', 'input'); __set(t, 'abc', 'change'); }`);
    await settle();
    const restored = await evalJs(`__rows('Income tax brackets')[3].querySelector('input[type=text]').value`);
    check("brackets: an invalid threshold is rejected and restored", restored === "$315k", restored);
    sharedHash = await evalJs("location.hash");
    await shot("simulator-brackets");
  });
  await step("presets: loading Warren", async () => {
    await evalJs(`{ const sel = document.querySelector('.panel select'); sel.value = 'warren';
      sel.dispatchEvent(new Event('change', { bubbles: true })); }`);
    await settle();
    const s = await stats();
    check("presets: loading Warren gives +10.19% (health not counted)", s[0] === "+10.19%", s);
  });
  await step("links: a shared link reproduces the plan", async () => {
    await go(BASE + sharedHash, ".stat .value");
    const s = await stats();
    check("links: a shared link reproduces the plan", bracketRevenue && s[0] === bracketRevenue, { expected: bracketRevenue, got: s[0] });
  });

  // ---- Figures page
  await step("figures page", async () => {
    await go(BASE + "#/figures", ".plans-table");
    const n = await evalJs(`document.querySelectorAll('.chart svg').length`);
    check("figures: all 8 charts render", n === 8, n);
    const rev = await evalJs(`[...document.querySelectorAll('.plans-table tbody tr')].map(r => [r.cells[0].textContent, r.cells[1].textContent])`);
    const want = { "Biden": "+1.51%", "Buttigieg": "+3.88%", "Sanders": "+2.91%", "Warren": "+3.24%" };
    const got = Object.fromEntries(rev.filter(([k]) => k in want));
    check("figures: revenue table reproduces the published figures", Object.keys(want).every((k) => got[k] === want[k]), got);
    const box = await evalJs(`(() => { const r = document.querySelector('[aria-label^="Total tax rates by income group for each plan"] svg').getBoundingClientRect();
      return [r.left + r.width * 0.85, r.top + r.height * 0.5]; })()`);
    await S("Input.dispatchMouseEvent", { type: "mouseMoved", x: box[0], y: box[1] });
    await settle();
    const tip = await evalJs(`document.querySelector('.tooltip')?.innerText ?? ''`);
    check("figures: hovering a chart shows a tooltip with every plan", ["Sanders", "Warren", "Biden", "Current law"].every((s) => tip.includes(s)), tip.split("\n").slice(0, 3));
    await evalJs(`{ const sel = document.getElementById('plan-select'); sel.value = 'sanders';
      sel.dispatchEvent(new Event('change', { bubbles: true })); }`);
    await settle();
    const sub = await evalJs(`[...document.querySelectorAll('.plan-name')].map(e => e.textContent)`);
    check("figures: the plan selector updates every detail chart", sub.length >= 5 && sub.every((t) => t === "Sanders"), sub);
    await shot("figures");
  });

  // ---- Standalone single-file build
  if (standalone) {
    await step("standalone build", async () => {
      await go(pathToFileURL(standalone).href + "#/figures", ".plans-table");
      const cells = await evalJs(`[...document.querySelectorAll('.plans-table tbody tr')].map(r => r.cells[1].textContent)`);
      check("standalone: the single-file build works from disk", cells.includes("+3.24%"), cells);
    });
  }

  const errors = events.filter((e) => e.method === "Runtime.exceptionThrown" ||
    (e.method === "Runtime.consoleAPICalled" && e.params.type === "error"));
  check("no console errors or uncaught exceptions", errors.length === 0,
    errors.map((e) => (e.params.exceptionDetails?.exception?.description ?? JSON.stringify(e.params.args)).slice(0, 200)));
} catch (e) {
  results.push({ name: "test harness", ok: false, detail: String(e) });
} finally {
  cleanup();
}

for (const r of results) console.log(`${r.ok ? "PASS" : "FAIL"}  ${r.name}${r.ok ? "" : "  " + JSON.stringify(r.detail)}`);
const failed = results.filter((r) => !r.ok).length;
console.log(`\n${results.length - failed}/${results.length} browser checks passed`);
process.exit(failed === 0 ? 0 : 1);
