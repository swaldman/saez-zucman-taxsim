package taxsim.parity

import taxsim.data.sz2019.Sz2019
import taxsim.data.us.UsModel
import taxsim.model.*
import taxsim.sz.*

/** The generic, lever-based US model must reproduce the faithful workbook port ([[SzModel]],
 *  itself checked cell-by-cell against the workbook) for every preset, tax by tax. */
class GenericModelEquivalenceTest extends munit.FunSuite:
  private val model = Sz2019.model
  private val tol = 1e-12

  private def close(what: String, a: Double, b: Double): Unit =
    assert(math.abs(a - b) <= tol * math.max(1.0, math.abs(b)), s"$what: generic $a vs workbook port $b")

  for plan <- Sz2019.presets.keys.toSeq.sorted; health <- Seq(false, true) do
    test(s"'$plan' (health as tax = $health): every tax, every group") {
      val opts = Sz2019.options.copy(healthInsuranceAsTax = health)
      val ref = SzModel.run(Sz2019.groups, Sz2019.current, Sz2019.presets(plan), opts)
      val out = Engine.run(model, model.presets(plan), UsModel.optionsOf(opts))
      assertEquals(out.instrumentIds.contains("health"), health)
      for (r, o) <- ref.groups.zip(out.groups) do
        val p = o.proposed
        val lbl = s"group ${r.group.label}"
        // Baseline = Σ component rates. The workbook's E uses the stored total `effrate`, which differs
        // from the sum of the stored (float32) components by rounding residuals; see the test below.
        assert(math.abs(o.baselineTotal - r.currentRate) <= maxEffrateResidual, s"$lbl current total")
        close(s"$lbl property", p("property"), r.proposed.property)
        close(s"$lbl payroll", p("payroll"), r.proposed.payroll)
        close(s"$lbl wealth+ftt", p("wealth") + p("ftt"), r.proposed.wealth)
        close(s"$lbl nit", p("nit"), r.proposed.nationalIncome)
        close(s"$lbl vat", p("vat"), r.proposed.vat)
        close(s"$lbl health", p.getOrElse("health", 0.0), r.proposed.healthInsurance)
        close(s"$lbl estate", p("estate"), r.proposed.estate)
        close(s"$lbl corporate", p("corporate"), r.proposed.corporate)
        close(s"$lbl income tax", p("incomeTax"), r.proposed.incomeTax)
        close(s"$lbl sales", p("sales"), r.proposed.sales)
        close(s"$lbl total", o.proposedTotal, r.proposedRate)
      close("proposed average rate", out.proposedAverageRate, ref.proposedAverageRate)
      assert(math.abs(out.revenueShareOfIncome - ref.revenueShareOfIncome) <= maxEffrateResidual)
    }

  /** Largest |effrate − Σ components| in the data: float32 rounding in the Stata output. */
  private lazy val maxEffrateResidual: Double =
    Sz2019.groups.map { g =>
      math.abs(g.baselineTotalRate - (g.salesTaxRate + g.propertyTaxRate + g.payrollTaxRate + g.incomeTaxRate +
        g.corporateTaxRate + g.estateTaxRate))
    }.max

  test("effrate equals the sum of its components up to float32 rounding") {
    println(f"max |effrate - sum of components| = $maxEffrateResidual%.3e")
    assert(maxEffrateResidual < 1e-7)
  }

  test("behavioral block on the generic outcome equals the workbook port") {
    val ref = SzBehavioral.run(SzModel.run(Sz2019.groups, Sz2019.current, Sz2019.presets("sanders"), Sz2019.options), Sz2019.growth, Sz2019.options)
    val out = Engine.run(model, model.presets("sanders"))
    val b = Behavioral.run(Behavioral.fromOutcome(out, Sz2019.growth.endYearIncome, Sz2019.growth.missingIncomeRaise),
      UsModel.behavioralSettings(Sz2019.options, Sz2019.params))
    // Baselines differ by the float32 effrate residual (≤ 1e-8), which the slope-based MTRs amplify slightly.
    for (x, y) <- b.groups.zip(ref.groups) do
      assert(math.abs(x.dynamicIncome / y.dynamicIncome - 1) < 1e-7, s"DI ${x.dynamicIncome} vs ${y.dynamicIncome}")
      assert(math.abs(x.aftertaxIncomeGainDynamic - y.aftertaxIncomeGainDynamic) <= 0.001 + 1e-12, "DM (rounded to 0.1pp)")
    assert(math.abs(b.aggregateAftertaxIncomeGain - ref.aggregateAftertaxIncomeGain) <= 0.001 + 1e-12)
  }

  test("a zero reform raises no revenue and leaves every rate unchanged") {
    val out = Engine.run(model, Policy.empty)
    for o <- out.groups; (id, r) <- o.proposed do close(s"${o.group.label} $id", r, o.baseline(id))
    close("revenue", out.revenueShareOfIncome, 0.0)
  }

  test("presets are valid against the lever schema") {
    for (name, p) <- model.presets do assertEquals(model.schema.validate(p), Nil, name)
    assertEquals(model.schema.validate(model.currentPolicy), Nil)
  }
