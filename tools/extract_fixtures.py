#!/usr/bin/env python3
"""Extract inputs, presets, and cached outputs from the Saez-Zucman workbook.

Writes:
  fixtures/sz2019/databig.csv            per-g-percentile inputs (sheet `databig`, rows 2..129)
  fixtures/sz2019/growth.csv             1980->2018 growth inputs (sheet `growth`, rows 2..129)
  fixtures/sz2019/presets.json           tax plans from sheet `interface` (columns C, D, G..L)
  fixtures/sz2019/calculatorbig_expected.csv  cached outputs of sheet `calculatorbig` (rows 8..135)
  fixtures/sz2019/calculatorbig_totals.json   cached totals (row 137, Z138, etc.)
and generated Scala sources embedding the same data (so tests run on JVM and Scala.js):
  data/src/taxsim/data/sz2019/Sz2019Raw.scala
  parity/test/src/taxsim/parity/ExpectedRaw.scala

Usage: python3 -I tools/extract_fixtures.py reference/taxsimulator2.xlsx
"""
import csv
import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from xlsx_cells import load, col_to_num, num_to_col  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "fixtures", "sz2019")

FIRST_ROW, LAST_ROW = 8, 135  # calculatorbig group rows
N = LAST_ROW - FIRST_ROW + 1   # 128 groups


def grid(cells):
    g = {}
    for ref, row, col, f, v in cells:
        g[(row, col)] = (f, v)
    return g


def val(g, row, col, default=""):
    return g.get((row, col), ("", default))[1]


def table(g, rows, cols):
    return [[val(g, r, c) for c in cols] for r in rows]


def cols_range(a, b):
    return [num_to_col(i) for i in range(col_to_num(a), col_to_num(b) + 1)]


def to_csv(header, rows):
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(header)
    w.writerows(rows)
    return buf.getvalue()


def scala_string_chunks(name, text, chunk=40000):
    parts = [text[i:i + chunk] for i in range(0, len(text), chunk)]
    lits = []
    for p in parts:
        assert '"""' not in p
        lits.append('    """' + p + '"""')
    return f"  val {name}: String = Seq(\n" + ",\n".join(lits) + "\n  ).mkString\n"


def main(wb):
    os.makedirs(OUT, exist_ok=True)

    # --- databig: header row 1, data rows 2..129
    db = grid(load(wb, "databig"))
    db_cols = cols_range("A", "BB")
    db_header = [val(db, 1, c) for c in db_cols]
    db_rows = table(db, range(2, 130), db_cols)
    databig_csv = to_csv(db_header, db_rows)

    # --- growth: we keep A..M rows 2..129 with short machine names
    gr = grid(load(wb, "growth"))
    gr_cols = cols_range("A", "M")
    gr_header = ["gperc", "nb1980", "peinc1980", "taxrate1980", "postinc1980",
                 "nb2018", "peinc2018", "taxrate2018", "postinc2018",
                 "peinc1980eq", "postinc1980eq", "missingPretaxRaise", "missingPosttaxRaise"]
    gr_rows = table(gr, range(2, 130), gr_cols)
    growth_csv = to_csv(gr_header, gr_rows)

    # --- presets from interface
    it = grid(load(wb, "interface"))
    preset_cols = {"C": "current", "D": "active", "G": "book", "H": "biden",
                   "I": "buttigieg", "J": "sanders", "K": "warren", "L": "obama"}

    def num(x, default=0.0):
        try:
            return float(x)
        except (TypeError, ValueError):
            return default

    def yes(x):
        return str(x).strip().lower() == "yes"

    presets = {}
    for c, name in preset_cols.items():
        presets[name] = {
            "nationalIncomeTaxRate": num(val(it, 10, c)),
            "vatRate": num(val(it, 11, c)),
            "narrowPayrollTaxRate": num(val(it, 12, c)),
            "broadPayrollTaxRate": num(val(it, 13, c)),
            "agiBaseTaxRate": num(val(it, 14, c)),
            "salesTaxReduction": num(val(it, 15, c)),
            "privateHealthReduction": num(val(it, 17, c)),
            "healthHeadTax": yes(val(it, 18, c)),
            "wealthTaxEvasion": num(val(it, 20, c)),
            "wealthTaxRates": [num(val(it, r, c)) for r in range(22, 36)],
            "financialTransactionTax": yes(val(it, 36, c)),
            "estateTaxAvoidance": num(val(it, 39, c)),
            "estateTaxTopRate": num(val(it, 40, c)),
            "corporateEffectiveRate": num(val(it, 43, c)),
            "betterMultinationalTaxation": yes(val(it, 44, c)),
            "capitalGainsFullyTaxed": yes(val(it, 48, c)),
            "dividendsFullyTaxed": yes(val(it, 49, c)),
            "dividendCorporateIntegration": yes(val(it, 50, c)),
            "incomeTaxRates": [num(val(it, r, c)) for r in range(51, 63)],
        }
    options = {
        "healthInsuranceAsTax": yes(val(it, 16, "C")),
        "outputElasticity": num(val(it, 68, "B")),
        "bargainingElasticity": num(val(it, 69, "B")),
    }
    presets_json = json.dumps({"options": options, "presets": presets}, indent=2)

    # --- calculatorbig cached outputs
    cb = grid(load(wb, "calculatorbig"))
    out_cols = (["A", "B", "C", "E", "L"] + cols_range("Q", "Z")
                + ["BB", "BW", "CA", "CE", "CH", "CI"]
                + cols_range("CU", "DO") + ["EO", "EP", "FC", "FD"]
                + cols_range("EA", "EN"))
    exp_rows = table(cb, range(FIRST_ROW, LAST_ROW + 1), out_cols)
    expected_csv = to_csv(out_cols, exp_rows)
    totals = {f"{c}137": val(cb, 137, c) for c in cols_range("B", "Z") + cols_range("CU", "DM") + cols_range("EA", "EN")}
    totals["Z138"] = val(cb, 138, "Z")
    totals["interface!W8"] = val(it, 8, "W")
    totals["interface!X8"] = val(it, 8, "X")
    totals["interface!Y8"] = val(it, 8, "Y")
    totals = {k: v for k, v in totals.items() if v != ""}
    totals_json = json.dumps(totals, indent=2)

    for name, text in [("databig.csv", databig_csv), ("growth.csv", growth_csv),
                       ("presets.json", presets_json),
                       ("calculatorbig_expected.csv", expected_csv),
                       ("calculatorbig_totals.json", totals_json)]:
        with open(os.path.join(OUT, name), "w") as f:
            f.write(text)

    banner = ("// GENERATED by tools/extract_fixtures.py from reference/taxsimulator2.xlsx -- do not edit.\n")
    data_dir = os.path.join(ROOT, "data", "src", "taxsim", "data", "sz2019")
    os.makedirs(data_dir, exist_ok=True)
    with open(os.path.join(data_dir, "Sz2019Raw.scala"), "w") as f:
        f.write(banner + "package taxsim.data.sz2019\n\n"
                "/** Raw inputs of the Saez-Zucman 2019/2020 simulator (sheets `databig`, `growth`, `interface`). */\n"
                "private[sz2019] object Sz2019Raw:\n"
                + scala_string_chunks("databigCsv", databig_csv)
                + scala_string_chunks("growthCsv", growth_csv)
                + scala_string_chunks("presetsJson", presets_json))
    par_dir = os.path.join(ROOT, "parity", "test", "src", "taxsim", "parity")
    os.makedirs(par_dir, exist_ok=True)
    with open(os.path.join(par_dir, "ExpectedRaw.scala"), "w") as f:
        f.write(banner + "package taxsim.parity\n\n"
                "/** Cached values of sheet `calculatorbig` (rows 8..135) and totals. */\n"
                "object ExpectedRaw:\n"
                + scala_string_chunks("calculatorbigCsv", expected_csv)
                + scala_string_chunks("totalsJson", totals_json))
    print(f"databig {len(db_rows)} rows x {len(db_cols)} cols; growth {len(gr_rows)}; "
          f"expected {len(exp_rows)} x {len(out_cols)}; presets {list(presets)}")


if __name__ == "__main__":
    main(sys.argv[1])
