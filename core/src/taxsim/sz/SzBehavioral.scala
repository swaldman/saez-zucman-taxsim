package taxsim.sz

import taxsim.model.{Behavioral, BehavioralInputs, BehavioralSettings}

type SzBehavioralGroup = taxsim.model.BehavioralGroup
type SzBehavioralResult = taxsim.model.BehavioralResult

/** The long-run behavioral block of the workbook, applied to a [[SzResult]]. See [[taxsim.model.Behavioral]]. */
object SzBehavioral:

  def run(static: SzResult, growth: SzGrowth, options: SzOptions = SzOptions()): SzBehavioralResult =
    val p = static.params
    val gs = static.groups
    Behavioral.run(
      BehavioralInputs(gs.map(_.group.percentile), gs.map(_.group.adults), gs.map(_.group.income),
        gs.map(_.currentRate), gs.map(_.proposedRate), growth.endYearIncome, growth.missingIncomeRaise),
      settings(options, p)
    )

  def settings(options: SzOptions, p: SzParams): BehavioralSettings =
    BehavioralSettings(options.outputElasticity, options.bargainingElasticity, p.mtrCap,
      p.mtrSmoothingHalfWindow, p.mtrSmoothingPasses, p.bargainingPhaseStart, p.bargainingPhaseEnd)

  def phaseIn(percentile: Double, start: Double, end: Double): Double = Behavioral.phaseIn(percentile, start, end)
