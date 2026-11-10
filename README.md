# saez-zucman-taxsim-scala

A reusable implementation of the Saez–Zucman tax simulator
([taxjusticenow.org](https://taxjusticenow.org)), written in Scala 3. It compiles to both the JVM and
Scala.js, so the same engine can run in a browser. The goal is to update it with recent US data and
then generalize it to other countries' tax systems.

**Status.** The 2020 model has been ported, and tests check it against the original workbook on the JVM
and on Scala.js. See [`docs/methodology.md`](docs/methodology.md) for the full model specification,
with references to the original spreadsheet cells.

## Layout

| Path | Contents |
|---|---|
| `core/` | Pure engine, with no dependencies. `taxsim.sz.SzModel` (static incidence) and `SzBehavioral` (long-run responses). |
| `data/` | Codecs (CSV, JSON via upickle) and embedded datasets. `taxsim.data.sz2019.Sz2019` holds the 2018 US data and the 2020 plans. |
| `parity/` | Tests that reproduce `reference/taxsimulator2.xlsx`. |
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
```

## Example

```scala
import taxsim.sz.*
import taxsim.data.sz2019.Sz2019

val plan = Sz2019.presets("warren").copy(vatRate = 0.05)
val r = SzModel.run(Sz2019.groups, Sz2019.current, plan, Sz2019.options)
r.revenueShareOfIncome            // net revenue, share of national income
r.groups.map(g => g.group.label -> g.proposedRate)
SzBehavioral.run(r, Sz2019.growth, Sz2019.options)   // optional long-run responses
```

## Credits

The model, the data and the original workbook are by Emmanuel Saez and Gabriel Zucman
(*The Triumph of Injustice*, 2019; taxjusticenow.org). This repository is an independent
reimplementation.
