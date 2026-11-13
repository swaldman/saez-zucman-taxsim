package taxsim.model

import taxsim.core.Numerics

/** Per-group outcome of the long-run behavioral block (calculatorbig columns CU..DO). */
final case class BehavioralGroup(
    currentMtr: Double,                // [DB] smoothed implied marginal tax rate, current system
    proposedMtr: Double,               // [DC] smoothed implied marginal tax rate, proposed system
    incomeAfterRealResponse: Double,   // [DD]
    incomeAfterBargaining: Double,     // [DE]
    bargainingGain: Double,            // [DF]
    missingIncome: Double,             // [DG]
    bottomBargainingGain: Double,      // [DH]
    dynamicIncome: Double,             // [DI]
    dynamicTaxes: Double,              // [DJ]
    pretaxIncomeGain: Double,          // [DK] rounded to 0.1pp, as displayed
    taxChange: Double,                 // [DL]
    aftertaxIncomeGainDynamic: Double, // [DM] rounded, as displayed
    aftertaxIncomeGainStatic: Double,  // [DN]
    missingIncomeRaise: Double         // [DO]
)

final case class BehavioralResult(groups: IndexedSeq[BehavioralGroup], aggregatePretaxIncomeChange: Double, aggregateAftertaxIncomeGain: Double)

/** Inputs to the behavioral block, aligned by group (ranked by income).
 *  @param endYearIncome actual pre-tax income in the end year of the growth comparison (e.g. 2018);
 *         the group's dollar shortfall is endYearIncome × missingIncomeRaise
 *  @param missingIncomeRaise   proportional shortfall versus equitable growth */
final case class BehavioralInputs(
    percentile: IndexedSeq[Double],
    adults: IndexedSeq[Double],
    income: IndexedSeq[Double],
    currentRate: IndexedSeq[Double],
    proposedRate: IndexedSeq[Double],
    endYearIncome: IndexedSeq[Double],
    missingIncomeRaise: IndexedSeq[Double]
)

final case class BehavioralSettings(
    outputElasticity: Double = 0.25,
    bargainingElasticity: Double = 0.5,
    mtrCap: Double = 0.99,
    smoothingHalfWindow: Int = 3,
    smoothingPasses: Int = 7,
    bargainingPhaseStart: Double = 89.0,
    bargainingPhaseEnd: Double = 99.0
)

/** Long-run behavioral responses (Saez-Zucman):
 *  1. implied marginal tax rates from the slope of after-tax on pre-tax income across adjacent
 *     groups, smoothed by repeated moving averages;
 *  2. a real (output) response, income ∝ (net-of-tax rate)^outputElasticity;
 *  3. a bargaining response at the top, phased in between two percentiles, whose income loss
 *     is redistributed in proportion to each group's shortfall relative to equitable growth. */
object Behavioral:

  def fromOutcome(o: Outcome, endYearIncome: IndexedSeq[Double], missingIncomeRaise: IndexedSeq[Double]): BehavioralInputs =
    val gs = o.groups
    BehavioralInputs(gs.map(_.group.percentile), gs.map(_.group.adults), gs.map(_.group.income),
      gs.map(_.baselineTotal), gs.map(_.proposedTotal), endYearIncome, missingIncomeRaise)

  def run(in: BehavioralInputs, s: BehavioralSettings): BehavioralResult =
    val m = in.income.length
    require(Seq(in.percentile, in.adults, in.currentRate, in.proposedRate, in.endYearIncome, in.missingIncomeRaise).forall(_.length == m),
      "behavioral inputs not aligned")
    val n = in.adults
    val y = in.income
    val curRate = in.currentRate
    val newRate = in.proposedRate
    val curNet = IndexedSeq.tabulate(m)(i => y(i) * (1 - curRate(i)))
    val newNet = IndexedSeq.tabulate(m)(i => y(i) * (1 - newRate(i)))

    // Slope to the next group; beyond either end the workbook compares to an empty row (0, 0).
    def slope(net: IndexedSeq[Double])(i: Int): Double =
      val (yA, nA) = if i < 0 then (0.0, 0.0) else (y(i), net(i))
      val (yB, nB) = if i + 1 >= m then (0.0, 0.0) else (y(i + 1), net(i + 1))
      (nB - nA) / (yB - yA)
    def impliedMtr(net: IndexedSeq[Double]) =
      IndexedSeq.tabulate(m)(i => math.min(1 - (slope(net)(i) + slope(net)(i - 1)) / 2, s.mtrCap))
    val curMtr = Numerics.smooth(impliedMtr(curNet), s.smoothingHalfWindow, s.smoothingPasses)
    val newMtr = Numerics.smooth(impliedMtr(newNet), s.smoothingHalfWindow, s.smoothingPasses)

    val ratio = IndexedSeq.tabulate(m)(i => (1 - newMtr(i)) / (1 - curMtr(i)))
    val real = IndexedSeq.tabulate(m)(i => y(i) * math.pow(ratio(i), s.outputElasticity))
    val bargained = IndexedSeq.tabulate(m) { i =>
      val w = phaseIn(in.percentile(i), s.bargainingPhaseStart, s.bargainingPhaseEnd)
      if w <= 0 then real(i) else real(i) * math.pow(ratio(i), w * s.bargainingElasticity)
    }
    val gain = IndexedSeq.tabulate(m)(i => bargained(i) - real(i))
    val missing = IndexedSeq.tabulate(m)(i => math.max(in.endYearIncome(i) * in.missingIncomeRaise(i), 0.0))
    val avgGain = Numerics.mean(gain, n)
    val avgMissing = Numerics.mean(missing, n)
    val bottomGain = missing.map(d => -d * avgGain / avgMissing)
    val dynamic = IndexedSeq.tabulate(m)(i => bargained(i) + bottomGain(i))
    val dynTaxes = IndexedSeq.tabulate(m)(i => dynamic(i) * newRate(i))

    val out = IndexedSeq.tabulate(m) { i =>
      BehavioralGroup(
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
        missingIncomeRaise = in.missingIncomeRaise(i)
      )
    }
    val avgY = Numerics.mean(y, n)
    val avgCur = Numerics.incomeWeightedMean(curRate, n, y)
    val avgNew = Numerics.incomeWeightedMean(newRate, n, y)
    val aggPretax = Numerics.mean(real, n) / avgY - 1
    val aggAftertax = Numerics.excelRound(Numerics.mean(dynamic, n) * (1 - avgNew) / (avgY * (1 - avgCur)) - 1, 3)
    BehavioralResult(out, aggPretax, aggAftertax)

  /** 0 at or below `start`, 1 at or above `end`, linear in between. */
  def phaseIn(percentile: Double, start: Double, end: Double): Double =
    math.min(1.0, math.max(0.0, (percentile - start) / (end - start)))
