#!/usr/bin/env python3
"""Dump the cells of an .xlsx worksheet (formula + cached value) using only the stdlib.

Shared formulas are expanded: each dependent cell gets the master formula with its
relative references shifted, which is what Excel does internally.

Usage:
  python3 -I tools/xlsx_cells.py WORKBOOK.xlsx SHEET_NAME [--rows A:B] [--cols C1:C2] [--csv OUT]

Output columns: ref, row, col, formula, value
"""
import argparse
import csv
import html
import re
import sys
import zipfile

NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


def col_to_num(col: str) -> int:
    n = 0
    for ch in col:
        n = n * 26 + (ord(ch) - 64)
    return n


def num_to_col(n: int) -> str:
    s = ""
    while n > 0:
        n, r = divmod(n - 1, 26)
        s = chr(65 + r) + s
    return s


def split_ref(ref: str):
    m = re.match(r"([A-Z]+)(\d+)$", ref)
    return m.group(1), int(m.group(2))


# A1-style reference, optionally with $ anchors. Must not be preceded by a letter/digit/_ or
# '!' handled separately (sheet-qualified refs shift the same way), and not followed by '(' (function).
REF_RE = re.compile(r"(?<![A-Za-z0-9_.])(\$?)([A-Z]{1,3})(\$?)(\d+)(?![A-Za-z0-9_(])")


def shift_formula(formula: str, dr: int, dc: int) -> str:
    """Shift relative references in formula by (dr, dc), skipping string literals."""
    out = []
    parts = re.split(r'("(?:[^"]|"")*")', formula)
    for i, part in enumerate(parts):
        if i % 2 == 1:
            out.append(part)
            continue

        def repl(m):
            cabs, col, rabs, row = m.groups()
            c = col_to_num(col)
            r = int(row)
            if not cabs:
                c += dc
            if not rabs:
                r += dr
            return f"{cabs}{num_to_col(c)}{rabs}{r}"

        out.append(REF_RE.sub(repl, part))
    return "".join(out)


def load(workbook: str, sheet_name: str):
    z = zipfile.ZipFile(workbook)
    wb = z.read("xl/workbook.xml").decode()
    rels = z.read("xl/_rels/workbook.xml.rels").decode()
    relmap = {}
    for m in re.finditer(r"<Relationship\b([^>]*)/?>", rels):
        a = m.group(1)
        rid = re.search(r'Id="([^"]+)"', a).group(1)
        tgt = re.search(r'Target="([^"]+)"', a).group(1)
        relmap[rid] = tgt
    target = None
    for m in re.finditer(r"<sheet\b([^>]*)/>", wb):
        a = m.group(1)
        name = html.unescape(re.search(r'name="([^"]+)"', a).group(1))
        rid = re.search(r'r:id="([^"]+)"', a).group(1)
        if name == sheet_name:
            target = relmap[rid]
    if target is None:
        raise SystemExit(f"sheet not found: {sheet_name}")
    ss = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in re.findall(r"<si>(.*?)</si>", z.read("xl/sharedStrings.xml").decode(), re.S):
            ss.append(html.unescape("".join(re.findall(r"<t[^>]*>(.*?)</t>", si, re.S))))
    data = z.read("xl/" + target.lstrip("/").replace("xl/", "")).decode()
    shared = {}
    cells = []
    for m in re.finditer(r'<c r="([A-Z]+\d+)"([^>]*?)(?:/>|>(.*?)</c>)', data, re.S):
        ref, attrs, body = m.groups()
        body = body or ""
        col, row = split_ref(ref)
        formula = ""
        fm = re.search(r"<f([^>]*)>(.*?)</f>", body, re.S) or re.search(r"<f([^>]*)/>", body)
        if fm:
            fattrs = fm.group(1)
            ftext = html.unescape(fm.group(2)) if fm.lastindex and fm.lastindex >= 2 else ""
            si = re.search(r'si="(\d+)"', fattrs)
            if 't="shared"' in fattrs and si:
                if ftext:
                    shared[si.group(1)] = (row, col_to_num(col), ftext)
                    formula = ftext
                elif si.group(1) in shared:
                    r0, c0, f0 = shared[si.group(1)]
                    formula = shift_formula(f0, row - r0, col_to_num(col) - c0)
            else:
                formula = ftext
        v = re.search(r"<v>(.*?)</v>", body, re.S)
        val = html.unescape(v.group(1)) if v else ""
        if 't="s"' in attrs and val != "":
            val = ss[int(val)]
        elif 't="inlineStr"' in attrs:
            val = html.unescape("".join(re.findall(r"<t[^>]*>(.*?)</t>", body, re.S)))
        if formula or val != "":
            cells.append((ref, row, col, formula, val))
    return cells


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("workbook")
    ap.add_argument("sheet")
    ap.add_argument("--rows", help="row range a:b inclusive")
    ap.add_argument("--cols", help="column range A:Z inclusive")
    ap.add_argument("--csv", help="write CSV to this path instead of stdout listing")
    ap.add_argument("--maxlen", type=int, default=400)
    a = ap.parse_args()
    cells = load(a.workbook, a.sheet)
    if a.rows:
        lo, hi = (int(x) for x in a.rows.split(":"))
        cells = [c for c in cells if lo <= c[1] <= hi]
    if a.cols:
        lo, hi = (col_to_num(x) for x in a.cols.split(":"))
        cells = [c for c in cells if lo <= col_to_num(c[2]) <= hi]
    if a.csv:
        with open(a.csv, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["ref", "row", "col", "formula", "value"])
            w.writerows(cells)
    else:
        for ref, _, _, f, v in cells:
            print(f"{ref} | {f[:a.maxlen]} | {v[:120]}")


if __name__ == "__main__":
    main()
