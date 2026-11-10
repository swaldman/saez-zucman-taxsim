# saez-zucman-taxsim-scala

A reusable implementation of the Saez–Zucman tax simulator
([taxjusticenow.org](https://taxjusticenow.org)), written in Scala 3. It compiles to both the JVM and
Scala.js, so the same engine can run in a browser. The goal is to update it with recent US data and
then generalize it to other countries' tax systems.

**Status.**
- **Step 1–2 (done).** The 2020 model has been ported, and tests check it against the original workbook on the
  JVM and on Scala.js. See [`docs/methodology.md`](docs/methodology.md).
- **Step 3 (done).** A generic, lever-based engine (`taxsim.model`) with reusable tax instruments, brackets at any
  threshold, VAT support, and JSON plans. The US model is re-expressed with it and matches the port exactly.
  See [`docs/architecture.md`](docs/architecture.md).
- **Demo website.** `webapp/`, see Build below.
- Open questions for the authors: [`docs/questions.md`](docs/questions.md).
- Next: refreshed US data, then other countries.

## Layout

| Path | Contents |
|---|---|
| `core/` | Pure engine, with no dependencies. `taxsim.model`: the generic engine (`Model`, `Engine`, `Instruments`, `ExcessCurve`, `Behavioral`). `taxsim.sz`: the faithful workbook port (`SzModel`, `SzBehavioral`), kept as a reference. |
| `data/` | Codecs (CSV, policy JSON) and embedded datasets. `taxsim.data.sz2019.Sz2019` holds the 2018 US data and the 2020 plans, both as raw groups and as a generic `model`. |
| `parity/` | Tests that reproduce `reference/taxsimulator2.xlsx`. |
| `webapp/` | Demo website (Scala.js + Laminar, dependency-free SVG charts). Two pages: the 2020 plans (the workbook's charts, recomputed live) and a simulator with levers generated from the model's `LeverSchema`. |
| `fixtures/sz2019/` | Canonical CSV/JSON extracted from the workbook. |
| `tools/` | Python (stdlib only) workbook extractors: `xlsx_cells.py` dumps a sheet's formulas and values; `extract_fixtures.py` regenerates the fixtures and the embedded Scala sources. |
| `reference/` | The original workbook and PDFs by Saez & Zucman. |

## Build

Requires a JDK (17+) and Node.js for the Scala.js tests. Mill is bootstrapped by `./mill`.

```sh
./mill __.compile                 # everything, JVM + JS
./mill '__.test'                  # all tests, JVM + JS
./mill 'parity.{jvm,js}.test'     # workbook parity only
python3 -I tools/extract_fixtures.py reference/taxsimulator2.xlsx   # regenerate fixtures

./mill webapp.devSite             # demo site, fast build → out/webapp/devSite.dest/
./mill webapp.site                # demo site, optimized → out/webapp/site.dest/
./mill webapp.standalone          # one self-contained HTML file → out/webapp/standalone.dest/taxsim-demo.html
python3 -m http.server 8765 --directory out/webapp/devSite.dest       # then open http://127.0.0.1:8765
```

The site is static: deploy the contents of `site.dest` anywhere. The URL carries the plan
(`#/simulator?plan={...}`, the levers that differ from current law, in compact JSON), so any
scenario can be shared as a link.

## Example

```scala
import taxsim.model.*
import taxsim.data.PolicyJson
import taxsim.data.sz2019.Sz2019
import taxsim.data.us.UsModel.L

val us = Sz2019.model
// Warren's plan, plus a 5% VAT and a 60% income-tax rate above $3m (not a data threshold)
val warren = us.presets("warren")
val plan = warren
  .set(L.vatRate, 0.05)
  .set(L.pitSchedule, warren.schedule(L.pitSchedule).withMinimumRateAbove(3e6, 0.6))
val out = Engine.run(us, plan)
out.revenueShareOfIncome                       // net revenue, share of national income
out.annualRevenue                              // $bn per year
out.groups.map(g => g.group.label -> g.proposedTotal)
PolicyJson.write(plan)                         // shareable JSON
```

## Credits

The model, the data and the original workbook are by Emmanuel Saez and Gabriel Zucman
(*The Triumph of Injustice*, 2019; taxjusticenow.org). This repository is an independent
reimplementation.
