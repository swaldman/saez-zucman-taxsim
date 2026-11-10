package taxsim.model

import taxsim.model.Instruments.*
import taxsim.model.LeverKind.*

/** A synthetic European-style country: a 20% VAT with observed incidence, social contributions,
 *  a progressive income tax calibrated to observed rates, and no wealth tax. Exercises the generic
 *  engine on a tax mix very different from the US. */
class VatCountryTest extends munit.FunSuite:

  private val pitThresholds = IndexedSeq(0.0, 11e3, 28e3, 80e3, 180e3)
  private val wealthThresholds = IndexedSeq(0.0, 1e6, 10e6, 100e6, 1e9)

  /** Ten groups: deciles, with the top decile split into P90–99, P99–99.9 and P99.9–100. */
  private val groups: IndexedSeq[Group] =
    val spec = Seq( // (label, pct, share of adults, income, VAT rate, social contr., PIT rate, wealth)
      ("P0-20", 0.0, 0.20, 9e3, 0.140, 0.20, 0.01, 2e4),
      ("P20-40", 20.0, 0.20, 18e3, 0.110, 0.22, 0.04, 6e4),
      ("P40-60", 40.0, 0.20, 26e3, 0.095, 0.23, 0.07, 1.2e5),
      ("P60-80", 60.0, 0.20, 36e3, 0.085, 0.23, 0.10, 2.2e5),
      ("P80-90", 80.0, 0.10, 50e3, 0.075, 0.22, 0.13, 4e5),
      ("P90-99", 90.0, 0.09, 85e3, 0.060, 0.19, 0.17, 9e5),
      ("P99-99.9", 99.0, 0.009, 300e3, 0.040, 0.12, 0.22, 5e6),
      ("P99.9-100", 99.9, 0.001, 2.5e6, 0.020, 0.04, 0.18, 1.2e8)
    )
    spec.map { (label, pct, share, inc, vat, sc, pit, w) =>
      // Taxable income ≈ 80% of income; excess over each threshold for a narrow group ≈ max(TI − t, 0),
      // smoothed a little so curves are strictly convex.
      val ti = 0.8 * inc
      val pitEx = pitThresholds.map(t => math.max(ti - t, 0.0) + 0.02 * ti * math.exp(-t / ti))
      val wEx = wealthThresholds.map(t => if w > 1e7 then w * math.pow(math.max(t, 1e6) / 1e6, -0.5) * (if t == 0 then 1.0 else 0.9) else math.max(w - t, 0.0))
      Group(label, pct, share * 50e6, inc,
        vars = Map("vatRate" -> vat, "socialRate" -> sc, "pitRate" -> pit, "consumptionProfile" -> vat, "laborBase" -> 0.7 * inc),
        excess = Map("taxableIncome" -> ExcessCurve(pitThresholds, pitEx), "wealth" -> ExcessCurve(wealthThresholds, wEx)))
    }.toIndexedSeq

  private val schema = LeverSchema(IndexedSeq(
    LeverSpec("vat.rate", "Standard VAT rate", "Consumption", Slider(0, 0.3, 0.005)),
    LeverSpec("social.extra", "Extra social contribution rate on labor", "Payroll", Slider(0, 0.1, 0.005)),
    LeverSpec("pit.schedule", "Income tax brackets", "Income tax", Brackets(0.7)),
    LeverSpec("wealth.schedule", "Wealth tax brackets", "Wealth tax", Brackets(0.1)),
    LeverSpec("wealth.evasion", "Wealth tax evasion", "Wealth tax", Slider(0, 1, 0.05))
  ))

  private val currentPit = Schedule(pitThresholds, IndexedSeq(0.0, 0.11, 0.30, 0.41, 0.45))
  private val current = Policy(
    "vat.rate" -> LeverValue.Num(0.20),
    "social.extra" -> LeverValue.Num(0.0),
    "pit.schedule" -> LeverValue.Sched(currentPit),
    "wealth.schedule" -> LeverValue.Sched(Schedule(wealthThresholds, IndexedSeq.fill(5)(0.0))),
    "wealth.evasion" -> LeverValue.Num(0.15)
  )

  private val model = Model(
    id = "synthetic-vat", name = "Synthetic VAT country", year = 2024, groups = groups,
    instruments = IndexedSeq(
      ConsumptionTax("vat", "VAT", "vat.rate", "consumptionProfile", baseShareOfIncome = 0.45, consumerShare = 0.75,
        baselineVar = Some("vatRate")),
      Fixed("social", "Social contributions", TaxCategory.Payroll, "socialRate"),
      FlatTax("socialExtra", "Extra social contributions", TaxCategory.Payroll, "laborBase", "social.extra"),
      ScheduleTax("pit", "Income tax", TaxCategory.IncomeTax, "taxableIncome", Some("pit.schedule"),
        baselineVar = Some("pitRate"), crowdedOutBy = Seq("vat")),
      ScheduleTax("wealth", "Wealth tax", TaxCategory.Wealth, "wealth", Some("wealth.schedule"), evasionLever = Some("wealth.evasion"))
    ),
    schema = schema,
    macroData = Macro("EUR", nationalIncome = 2000.0),
    currentPolicy = current
  )

  private val tol = 1e-12

  test("current policy is valid and a zero reform changes nothing") {
    assertEquals(schema.validate(current), Nil)
    val out = Engine.run(model, Policy.empty)
    for o <- out.groups; (id, r) <- o.proposed do assertEqualsDouble(r, o.baseline(id), tol, s"${o.group.label} $id")
    assertEqualsDouble(out.revenueShareOfIncome, 0.0, tol)
  }

  test("raising the VAT rescales observed VAT incidence by the tax-inclusive rate ratio") {
    val out = Engine.run(model, Policy("vat.rate" -> LeverValue.Num(0.25)))
    val factor = (0.25 / 1.25) / (0.20 / 1.20)
    for o <- out.groups do assertEqualsDouble(o.proposed("vat"), o.baseline("vat") * factor, tol)
    assert(out.revenueShareOfIncome("vat") > 0)
  }

  test("a VAT increase is regressive: rates rise most at the bottom") {
    val out = Engine.run(model, Policy("vat.rate" -> LeverValue.Num(0.25)))
    val increases = out.groups.map(o => o.proposedTotal - o.baselineTotal)
    assert(increases.head > increases.last, s"bottom +${increases.head} vs top +${increases.last}")
  }

  test("the VAT crowds out the income tax base") {
    val out = Engine.run(model, Policy("vat.rate" -> LeverValue.Num(0.25)))
    for o <- out.groups do
      val expected = (1 - o.proposed("vat")) / (1 - o.baseline("vat")) * o.baseline("pit")
      assertEqualsDouble(o.proposed("pit"), expected, tol)
  }

  test("a wealth tax only hits groups with wealth above the threshold, net of evasion") {
    val wt = Schedule(wealthThresholds, IndexedSeq(0.0, 0.0, 0.02, 0.03, 0.03))
    val out = Engine.run(model, Policy("wealth.schedule" -> LeverValue.Sched(wt)))
    for o <- out.groups do
      val g = o.group
      val expected = 0.85 * (0.02 * g.excessOf("wealth").at(10e6) + 0.01 * g.excessOf("wealth").at(100e6)) / g.income
      assertEqualsDouble(o.proposed("wealth"), expected, tol, g.label)
    assertEqualsDouble(out.groups.take(6).map(_.proposed("wealth")).sum, 0.0, tol)
    assert(out.groups.last.proposed("wealth") > 0.01)
  }

  test("income tax brackets can be moved to arbitrary thresholds") {
    // New top bracket of 55% above €400k, a threshold that is not in the data grid.
    val reform = Schedule(pitThresholds :+ 400e3, currentPit.rates :+ 0.55)
    val out = Engine.run(model, Policy("pit.schedule" -> LeverValue.Sched(reform)))
    for o <- out.groups do
      val g = o.group
      val expected = g("pitRate") + 0.10 * g.excessOf("taxableIncome").at(400e3) / g.income
      assertEqualsDouble(o.proposed("pit"), expected, tol, g.label)
    assert(out.groups.last.proposed("pit") > out.groups.last.baseline("pit"))
    assertEqualsDouble(out.groups.head.proposed("pit"), out.groups.head.baseline("pit"), tol)
  }

  test("revenue in money terms uses the model's national income") {
    val out = Engine.run(model, Policy("social.extra" -> LeverValue.Num(0.01)))
    assertEqualsDouble(out.annualRevenue, out.revenueShareOfIncome * 2000.0, tol)
    // A 1% levy on a base of 70% of income raises 0.7% of income.
    assertEqualsDouble(out.revenueShareOfIncome, 0.007, 1e-12)
  }

  test("invalid levers are reported") {
    val bad = Policy("vat.rate" -> LeverValue.Num(0.5), "nope" -> LeverValue.Flag(true), "wealth.evasion" -> LeverValue.Flag(true))
    assertEquals(schema.validate(bad).length, 3)
  }
