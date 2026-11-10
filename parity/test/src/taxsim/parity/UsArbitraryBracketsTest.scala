package taxsim.parity

import taxsim.data.sz2019.Sz2019
import taxsim.data.us.UsModel.L
import taxsim.model.*

/** New capability of the generic engine: brackets at thresholds that are not in the data. */
class UsArbitraryBracketsTest extends munit.FunSuite:
  private val model = Sz2019.model
  private val cur = model.currentPolicy.schedule(L.pitSchedule)

  private def revenueWithTopBracketAt(t: Double, rate: Double): Double =
    val s = cur.withMinimumRateAbove(t, rate)
    Engine.run(model, Policy(L.pitSchedule -> LeverValue.Sched(s))).revenueShareOfIncome

  test("a new 50% bracket above $3m (between grid points $2.5m and $5m) raises revenue between the two grid cases") {
    val at25 = revenueWithTopBracketAt(2.5e6 + 1, 0.5)  // just above a grid point
    val at3 = revenueWithTopBracketAt(3e6, 0.5)
    val at5 = revenueWithTopBracketAt(5e6, 0.5)
    println(f"top bracket 50%% above $$2.5m: ${at25 * 100}%.3f%%, $$3m: ${at3 * 100}%.3f%%, $$5m: ${at5 * 100}%.3f%% of NI")
    assert(at25 > at3 && at3 > at5 && at5 > 0)
  }

  test("revenue is continuous and decreasing as the new threshold moves up") {
    val ts = (1 to 40).map(i => 1e6 * math.pow(1.15, i))
    val rs = ts.map(revenueWithTopBracketAt(_, 0.5))
    for i <- 1 until rs.length do
      assert(rs(i) <= rs(i - 1) + 1e-12, s"not decreasing at ${ts(i)}")
      assert(rs(i - 1) - rs(i) < 0.2 * rs(i - 1) + 1e-6, s"jump at ${ts(i)}")
  }

  test("a wealth tax starting at $750m (not a data threshold) falls between the $500m and $1bn cases") {
    def rev(t: Double) =
      val s = Schedule(IndexedSeq(0.0, t), IndexedSeq(0.0, 0.03))
      Engine.run(model, Policy(L.wealthSchedule -> LeverValue.Sched(s))).revenueShareOfIncome
    val (a, b, c) = (rev(500e6), rev(750e6), rev(1e9))
    assert(a > b && b > c && c > 0, s"$a $b $c")
  }
