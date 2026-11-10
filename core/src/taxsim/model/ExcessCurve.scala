package taxsim.model

/** For one income group, the average excess of a base (wealth, taxable income, …) over a
 *  threshold, X(t) = E[max(x − t, 0)], tabulated at grid thresholds and interpolated in between.
 *
 *  X is non-increasing and convex in t, with slope −P(x > t) ∈ [−1, 0]. Between grid points:
 *   - when both endpoints are positive and t_k > 0, we interpolate log-linearly in (log t, log X).
 *     This is exact when the group's distribution has a Pareto tail on the interval:
 *     with survival ∝ t^(−a), X(t) ∝ t^(1−a). The result is clamped to the lower bound below.
 *   - otherwise (the first segment from t = 0, or the last segment before X reaches 0) the true
 *     value lies between the chord (an upper bound, by convexity) and max(X_k − (t − t_k), X_{k+1})
 *     (a lower bound, since the slope is ≥ −1). We take the midpoint of the two bounds.
 *  Above the last grid point we extend the last Pareto segment, or return 0 if X is already 0.
 *  At grid thresholds the curve returns the tabulated values exactly.
 */
final case class ExcessCurve(thresholds: IndexedSeq[Double], values: IndexedSeq[Double]):
  require(thresholds.nonEmpty && thresholds.length == values.length, "thresholds/values mismatch")
  require(thresholds.indices.drop(1).forall(k => thresholds(k) > thresholds(k - 1)), s"thresholds must increase: $thresholds")

  def apply(t: Double): Double = at(t)

  def at(t: Double): Double =
    val n = thresholds.length
    if t <= thresholds(0) then values(0) // below the grid: no information, hold the first value
    else
      // locate segment k with t_k < t ≤ t_{k+1}
      var k = 0
      while k + 1 < n && thresholds(k + 1) < t do k += 1
      if k + 1 < n then
        if thresholds(k + 1) == t then values(k + 1) else segment(k, t)
      else extrapolate(t)

  private def segment(k: Int, t: Double): Double =
    val (t0, t1, x0, x1) = (thresholds(k), thresholds(k + 1), values(k), values(k + 1))
    if x0 <= 0.0 then 0.0
    else if x1 > 0.0 && t0 > 0.0 then
      val b = math.log(x1 / x0) / math.log(t1 / t0)
      // Convex, so below the chord; clamp to the slope ≥ −1 lower bound where the tail is not Pareto.
      math.max(x0 * math.pow(t / t0, b), math.max(x0 - (t - t0), x1))
    else
      val chord = x0 + (x1 - x0) * (t - t0) / (t1 - t0)
      val lower = math.max(x0 - (t - t0), x1)
      (chord + math.min(lower, chord)) / 2

  private def extrapolate(t: Double): Double =
    val n = thresholds.length
    val (tl, xl) = (thresholds(n - 1), values(n - 1))
    if xl <= 0.0 then 0.0
    else if n >= 2 && values(n - 2) > 0.0 && thresholds(n - 2) > 0.0 && xl < values(n - 2) then
      val b = math.log(xl / values(n - 2)) / math.log(tl / thresholds(n - 2))
      xl * math.pow(t / tl, b)
    else math.max(xl - (t - tl), 0.0) // no tail information: lower bound

object ExcessCurve:
  val zero: ExcessCurve = ExcessCurve(IndexedSeq(0.0), IndexedSeq(0.0))
