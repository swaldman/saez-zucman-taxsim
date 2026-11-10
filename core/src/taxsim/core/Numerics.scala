package taxsim.core

/** Small numeric helpers shared by models. Everything here is pure and platform-independent. */
object Numerics:

  /** Population-weighted mean: Σ x·w / Σ w. */
  def mean(x: IndexedSeq[Double], w: IndexedSeq[Double]): Double =
    require(x.length == w.length, s"length mismatch: ${x.length} vs ${w.length}")
    var num = 0.0
    var den = 0.0
    var i = 0
    while i < x.length do
      num += x(i) * w(i)
      den += w(i)
      i += 1
    num / den

  /** Mean of a rate weighted by population × income: Σ r·n·y / Σ n·y. This is how the
   *  Saez-Zucman workbook aggregates effective tax rates (so the result is total tax / total income). */
  def incomeWeightedMean(rate: IndexedSeq[Double], n: IndexedSeq[Double], y: IndexedSeq[Double]): Double =
    require(rate.length == n.length && n.length == y.length, "length mismatch")
    var num = 0.0
    var den = 0.0
    var i = 0
    while i < rate.length do
      val w = n(i) * y(i)
      num += rate(i) * w
      den += w
      i += 1
    num / den

  /** Excel's ROUND(x, digits): rounds half away from zero. */
  def excelRound(x: Double, digits: Int): Double =
    val f = math.pow(10, digits)
    // Nudge by a relative epsilon to emulate Excel's decimal-aware rounding of values like 0.0445.
    val scaled = math.abs(x) * f
    val r = math.floor(scaled + 0.5 + 1e-9 * math.max(1.0, scaled))
    math.signum(x) * r / f

  /** One pass of a centered moving average of width `2*half + 1`, replicating edge values. */
  def movingAverage(xs: IndexedSeq[Double], half: Int): IndexedSeq[Double] =
    val n = xs.length
    def at(i: Int): Double = xs(math.min(math.max(i, 0), n - 1))
    IndexedSeq.tabulate(n) { i =>
      var s = 0.0
      var k = -half
      while k <= half do
        s += at(i + k)
        k += 1
      s / (2 * half + 1)
    }

  /** `passes` repeated centered moving averages (edge-replicating). */
  def smooth(xs: IndexedSeq[Double], half: Int, passes: Int): IndexedSeq[Double] =
    (0 until passes).foldLeft(xs)((acc, _) => movingAverage(acc, half))

  /** Σ_k (r_k − r_{k−1}) · excess_k, with r_{−1} = 0. Excess_k is the group's average amount of
   *  the base in excess of the k-th bracket threshold, so this is the average tax due under a
   *  piecewise-linear marginal-rate schedule. */
  def bracketTax(marginalRates: IndexedSeq[Double], excess: IndexedSeq[Double]): Double =
    require(marginalRates.length == excess.length, s"rates ${marginalRates.length} vs bases ${excess.length}")
    var s = 0.0
    var prev = 0.0
    var k = 0
    while k < marginalRates.length do
      s += (marginalRates(k) - prev) * excess(k)
      prev = marginalRates(k)
      k += 1
    s
