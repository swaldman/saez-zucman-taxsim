package taxsim.parity

import taxsim.core.Numerics
import taxsim.data.sz2019.Sz2019
import taxsim.sz.*

/** Keeps the worked examples in `docs/methodology.md` §10 honest: the inputs quoted from
 *  `databig`, the intermediate dollar amounts computed by hand, and the final rates must match
 *  the data and the engine. If this test fails, update the document (or the data) together. */
class WorkedExamplesTest extends munit.FunSuite:
  private val warren = Sz2019.presets("warren")
  private val result = SzModel.run(Sz2019.groups, Sz2019.current, warren, Sz2019.options)
  private val row = result.groups.find(r => math.abs(r.group.percentile - 99.997) < 1e-6).get
  private val g = row.group
  private val y = g.income

  /** Dollar amounts as printed in the document (rounded to the dollar). */
  private def assertDollars(actual: Double, documented: Double)(using munit.Location): Unit =
    assertEqualsDouble(actual, documented, 1.0)
  /** Rates as printed in the document (5 decimals). */
  private def assertRate(actual: Double, documented: Double)(using munit.Location): Unit =
    assertEqualsDouble(actual, documented, 5e-6)

  test("inputs quoted from databig (P99.997–99.998)") {
    assertEqualsDouble(g.adults, 1938.0, 0.0)
    assertDollars(y, 40647524)
    // wealth excesses above 0, 1m, 50m, 1bn
    assertDollars(g.wealthExcess(0), 331388704)
    assertDollars(g.wealthExcess(1), 330798560)
    assertDollars(g.wealthExcess(6), 301881120)
    assertDollars(g.wealthExcess(10), 5011823)
    assert(g.wealthExcess.drop(11).forall(_ == 0.0))
    // income tax inputs
    assertRate(g.incomeTaxRate, 0.19687)
    assertDollars(g.fiscalIncome, 25726080)
    assertDollars(g.capitalGains, 13170995)
    assertDollars(g.dividends, 1819416)
    assertEqualsDouble(g.marginalRateAbove20, 0.167, 1e-6)
    assertDollars(g.taxableIncomeExcess(3), 23762612) // above 315k
    assertDollars(g.taxableIncomeExcess(4), 23720880) // above 400k
    assertDollars(g.taxableIncomeExcess(5), 23622688) // above 600k
  }

  test("wealth tax worked example (Warren, with FTT)") {
    val p = SzParams()
    val rates = warren.wealthTaxRates.zipWithIndex.map { (r, k) =>
      r + (if k == 0 then p.fttWealthSurchargeFirstBracket else p.fttWealthSurcharge)
    }
    val excess = g.wealthExcess.padTo(rates.length, 0.0)
    val tax = Numerics.bracketTax(rates, excess)
    assertDollars(tax, 6569189)
    val afterEvasion = tax * (1 - warren.wealthTaxEvasion)
    assertDollars(afterEvasion, 5583811)
    assertRate(afterEvasion / y, 0.13737)
    assertEqualsDouble(row.proposed.wealth, afterEvasion / y, 1e-12)
    assertEqualsDouble(g.wealth / y, 8.15, 0.005)
  }

  test("income tax worked example (Warren)") {
    val delta = warren.incomeTaxRates.lazyZip(Sz2019.current.incomeTaxRates).map(_ - _)
    val dB = Numerics.bracketTax(delta, g.taxableIncomeExcess.padTo(delta.length, 0.0))
    assertDollars(dB, 4273809)
    assertRate(g.capitalGains / g.fiscalIncome, 0.51197)
    assertRate(g.dividends / g.fiscalIncome, 0.07072)
    assertDollars((1 - g.capitalGains / g.fiscalIncome - g.dividends / g.fiscalIncome) * dB, 1783490)
    val chKg = g.capitalGains * g.marginalRateAbove20
    val chDiv = g.dividends * g.marginalRateAbove20
    assertDollars(chKg, 2199556)
    assertDollars(chDiv, 303842)
    val w = g.incomeTaxRate + (chKg + chDiv + dB) / y
    assertRate(w, 0.36360)
    assertEqualsDouble(row.proposed.incomeTax, w, 1e-12) // no NIT or VAT: crowd-out factor is 1
    assertRate(dB / y, 0.10514)
    assertRate(chKg / y, 0.05411)
    assertRate(chDiv / y, 0.007475)
  }
