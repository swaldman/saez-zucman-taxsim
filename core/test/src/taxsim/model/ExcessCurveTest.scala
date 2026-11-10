package taxsim.model

class ExcessCurveTest extends munit.FunSuite:

  /** Exact E[max(x − t, 0)] for a sample of values. */
  private def exact(xs: Seq[Double])(t: Double): Double = xs.map(x => math.max(x - t, 0.0)).sum / xs.size

  test("returns tabulated values exactly at grid thresholds") {
    val c = ExcessCurve(IndexedSeq(0, 10, 100, 1000), IndexedSeq(500, 450, 300, 40))
    for (t, x) <- c.thresholds.zip(c.values) do assertEqualsDouble(c.at(t), x, 0.0)
  }

  test("Pareto interpolation is exact for a Pareto tail") {
    // Survival S(t) = (t/1)^-a above t = 1 (a = 2.5): X(t) = t^(1-a)/(a-1) for t ≥ 1.
    val a = 2.5
    def x(t: Double) = math.pow(t, 1 - a) / (a - 1)
    val grid = IndexedSeq(1.0, 10.0, 100.0, 1000.0)
    val c = ExcessCurve(grid, grid.map(x))
    for t <- Seq(1.5, 3.0, 7.0, 42.0, 333.0, 999.0, 5000.0, 1e5) do
      assertEqualsDouble(c.at(t) / x(t), 1.0, 1e-12, s"t = $t")
  }

  test("interpolated values respect the convexity bounds and are non-increasing") {
    val rnd = new scala.util.Random(42)
    for trial <- 1 to 200 do
      // Random group: lognormal-ish values.
      val xs = Seq.fill(50)(math.exp(rnd.nextGaussian() * 1.5 + 10))
      val grid = IndexedSeq(0.0, 1e4, 3e4, 1e5, 3e5, 1e6, 3e6)
      val c = ExcessCurve(grid, grid.map(exact(xs)))
      var prev = Double.PositiveInfinity
      for k <- 0 until grid.length - 1; j <- 1 to 9 do
        val t = grid(k) + (grid(k + 1) - grid(k)) * j / 10
        val v = c.at(t)
        val (x0, x1) = (c.values(k), c.values(k + 1))
        val chord = x0 + (x1 - x0) * (t - grid(k)) / (grid(k + 1) - grid(k))
        val lower = math.max(x0 - (t - grid(k)), x1)
        assert(v <= chord + 1e-6 && v >= lower - 1e-6, s"trial $trial t=$t: $v not in [$lower, $chord]")
        assert(v <= prev + 1e-6, s"not non-increasing at t=$t")
        prev = v
  }

  test("interpolation error is small for a realistic income group") {
    val rnd = new scala.util.Random(7)
    // Top-tail-like group: Pareto(a = 1.6) above $500k.
    val xs = Seq.fill(20000)(5e5 * math.pow(1 - rnd.nextDouble(), -1 / 1.6))
    val grid = IndexedSeq(0.0, 77e3, 165e3, 315e3, 400e3, 600e3, 1e6, 2.5e6, 5e6, 10e6, 25e6, 50e6)
    val c = ExcessCurve(grid, grid.map(exact(xs)))
    for t <- Seq(700e3, 1.5e6, 3e6, 7e6) do
      val rel = c.at(t) / exact(xs)(t) - 1
      assert(math.abs(rel) < 0.05, f"t=$t%.0f: relative error $rel%.4f")
  }

  test("Schedule.averageTax on an excess curve equals the per-person tax, averaged") {
    val xs = Seq(5e4, 1.2e5, 3e5, 8e5, 2e6)
    val sched = Schedule(IndexedSeq(0, 1e5, 5e5, 1e6), IndexedSeq(0.1, 0.2, 0.3, 0.4))
    def taxOf(x: Double) = sched.thresholds.indices.map { k =>
      val hi = if k + 1 < sched.thresholds.length then sched.thresholds(k + 1) else Double.PositiveInfinity
      sched.rates(k) * math.max(0.0, math.min(x, hi) - sched.thresholds(k))
    }.sum
    assertEqualsDouble(sched.averageTax(exact(xs)), xs.map(taxOf).sum / xs.size, 1e-6)
  }

  test("splitting a bracket without changing rates leaves the tax unchanged") {
    val c = ExcessCurve(IndexedSeq(0, 1e5, 1e6), IndexedSeq(2e5, 1.2e5, 3e4))
    val s1 = Schedule(IndexedSeq(0, 1e5), IndexedSeq(0.1, 0.3))
    val s2 = Schedule(IndexedSeq(0, 1e5, 4e5), IndexedSeq(0.1, 0.3, 0.3))
    assertEqualsDouble(s1.averageTax(c.at), s2.averageTax(c.at), 1e-9)
  }

  test("Schedule.withMinimumRateAbove splits and floors brackets") {
    val s = Schedule(IndexedSeq(0, 100, 1000), IndexedSeq(0.1, 0.3, 0.4)).withMinimumRateAbove(500, 0.35)
    assertEquals(s.thresholds, IndexedSeq(0.0, 100.0, 500.0, 1000.0))
    assertEquals(s.rates, IndexedSeq(0.1, 0.3, 0.35, 0.4))
    assertEquals(Schedule(IndexedSeq(0, 100), IndexedSeq(0.1, 0.3)).insertThreshold(50).rates, IndexedSeq(0.1, 0.1, 0.3))
  }
