package taxsim.sz

import taxsim.core.Numerics

/** Per-group outcome of the long-run behavioral block (calculatorbig columns CU..DO). */
final case class SzBehavioralGroup(
    currentMtr: Double,          // [DB] smoothed implied marginal tax rate, current system
    proposedMtr: Double,         // [DC] smoothed implied marginal tax rate, proposed system
    incomeAfterRealResponse: Double, // [DD]
    incomeAfterBargaining: Double,   // [DE]
    bargainingGain: Double,          // [DF]
    missingIncome: Double,           // [DG]
    bottomBargainingGain: Double,    // [DH]
    dynamicIncome: Double,           // [DI]
    dynamicTaxes: Double,            // [DJ]
    pretaxIncomeGain: Double,        // [DK] rounded to 0.1pp, as displayed
    taxChange: Double,               // [DL]
    aftertaxIncomeGainDynamic: Double, // [DM] rounded, as displayed
    aftertaxIncomeGainStatic: Double,  // [DN]
    missingIncomeRaise: Double         // [DO]
)

final case class SzBehavioralResult(groups: IndexedSeq[SzBehavioralGroup], aggregatePretaxIncomeChange: Double, aggregateAftertaxIncomeGain: Double)

/** The optional long-run behavioral responses of the simulator.
 *
 *  1. Implied marginal tax rates are recovered from the slope of after-tax income with respect to
 *     pre-tax income across adjacent groups, then smoothed.
 *  2. A "real" (output) response: pre-tax income scales with the net-of-tax rate to the power
 *     `outputElasticity`.
 *  3. A "bargaining" response at the top (phased in from P89 to P99): the extra pre-tax income
 *     lost at the top is not lost to the economy but is redistributed to groups in proportion to
 *     their shortfall relative to "equitable" growth since 1980.
 */
object SzBehavioral:

  def run(static: SzResult, growth: SzGrowth, options: SzOptions = SzOptions()): SzBehavioralResult =
    val p = static.params
    val gs = static.groups
    val m = gs.length
    require(growth.counterfactualIncome.length == m && growth.missingIncomeRaise.length == m, "growth data not aligned with groups")
    val n = gs.map(_.group.adults)
    val y = gs.map(_.group.income)                           // CW
    val curRate = gs.map(_.currentRate)                      // CU
    val newRate = gs.map(_.proposedRate)                     // CV
    val curNet = IndexedSeq.tabulate(m)(i => y(i) * (1 - curRate(i)))  // CX
    val newNet = IndexedSeq.tabulate(m)(i => y(i) * (1 - newRate(i)))  // CY

    // Slope to the next group; beyond the last group the spreadsheet compares to an empty row (0, 0).
    // Index -1 (row 7) compares the first group to (0, 0) as well.
    def slope(net: IndexedSeq[Double])(i: Int): Double =
      val (yA, nA) = if i < 0 then (0.0, 0.0) else (y(i), net(i))
      val (yB, nB) = if i + 1 >= m then (0.0, 0.0) else (y(i + 1), net(i + 1))
      (nB - nA) / (yB - yA)
    def impliedMtr(net: IndexedSeq[Double]) =
      IndexedSeq.tabulate(m)(i => math.min(1 - (slope(net)(i) + slope(net)(i - 1)) / 2, p.mtrCap))            // EO / EP
    val curMtr = Numerics.smooth(impliedMtr(curNet), p.mtrSmoothingHalfWindow, p.mtrSmoothingPasses)          // DB
    val newMtr = Numerics.smooth(impliedMtr(newNet), p.mtrSmoothingHalfWindow, p.mtrSmoothingPasses)          // DC

    val netOfTaxRatio = IndexedSeq.tabulate(m)(i => (1 - newMtr(i)) / (1 - curMtr(i)))
    val real = IndexedSeq.tabulate(m)(i => y(i) * math.pow(netOfTaxRatio(i), options.outputElasticity))     // DD
    val bargained = IndexedSeq.tabulate(m) { i =>                                                             // DE
      val w = phaseIn(gs(i).group.percentile, p.bargainingPhaseStart, p.bargainingPhaseEnd)
      if w <= 0 then real(i) else real(i) * math.pow(netOfTaxRatio(i), w * options.bargainingElasticity)
    }
    val gain = IndexedSeq.tabulate(m)(i => bargained(i) - real(i))                                            // DF
    val missing = IndexedSeq.tabulate(m)(i => math.max(growth.counterfactualIncome(i) * growth.missingIncomeRaise(i), 0.0)) // DG
    val avgGain = Numerics.mean(gain, n)                                                                      // DF137
    val avgMissing = Numerics.mean(missing, n)                                                                // DG137
    val bottomGain = missing.map(d => -d * avgGain / avgMissing)                                              // DH
    val dynamic = IndexedSeq.tabulate(m)(i => bargained(i) + bottomGain(i))                                   // DI
    val dynTaxes = IndexedSeq.tabulate(m)(i => dynamic(i) * newRate(i))                                       // DJ

    val out = IndexedSeq.tabulate(m) { i =>
      SzBehavioralGroup(
        currentMtr = curMtr(i),
        proposedMtr = newMtr(i),
        incomeAfterRealResponse = real(i),
        incomeAfterBargaining = bargained(i),
        bargainingGain = gain(i),
        missingIncome = missing(i),
        bottomBargainingGain = bottomGain(i),
        dynamicIncome = dynamic(i),
        dynamicTaxes = dynTaxes(i),
        pretaxIncomeGain = Numerics.excelRound(dynamic(i) / y(i) - 1, 3),
        taxChange = dynTaxes(i) - curRate(i) * y(i),
        aftertaxIncomeGainDynamic = Numerics.excelRound(dynamic(i) * (1 - newRate(i)) / (y(i) * (1 - curRate(i))) - 1, 3),
        aftertaxIncomeGainStatic = (curRate(i) - newRate(i)) / (1 - curRate(i)),
        missingIncomeRaise = growth.missingIncomeRaise(i)
      )
    }
    val avgY = Numerics.mean(y, n)
    val aggPretax = Numerics.mean(real, n) / avgY - 1                                                         // DK137
    val aggAftertax = Numerics.excelRound(
      Numerics.mean(dynamic, n) * (1 - static.proposedAverageRate) / (avgY * (1 - static.currentAverageRate)) - 1, 3) // DM137
    SzBehavioralResult(out, aggPretax, aggAftertax)

  /** 0 at or below `start`, 1 at or above `end`, linear in between. */
  def phaseIn(percentile: Double, start: Double, end: Double): Double =
    math.min(1.0, math.max(0.0, (percentile - start) / (end - start)))
