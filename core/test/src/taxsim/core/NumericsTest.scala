package taxsim.core

class NumericsTest extends munit.FunSuite:
  import Numerics.*

  test("bracketTax equals direct schedule computation for a single taxpayer") {
    // One taxpayer with base 150: rates 10% on [0,100), 30% above 100 → 10 + 15 = 25.
    val thresholds = IndexedSeq(0.0, 100.0)
    val base = 150.0
    val excess = thresholds.map(t => math.max(base - t, 0.0))
    assertEqualsDouble(bracketTax(IndexedSeq(0.10, 0.30), excess), 25.0, 1e-12)
  }

  test("bracketTax is linear in the excess (so it works on group averages)") {
    val ex1 = IndexedSeq(150.0, 50.0)
    val ex2 = IndexedSeq(80.0, 0.0)
    val avg = ex1.lazyZip(ex2).map((a, b) => (a + b) / 2)
    val r = IndexedSeq(0.1, 0.3)
    assertEqualsDouble(bracketTax(r, avg), (bracketTax(r, ex1) + bracketTax(r, ex2)) / 2, 1e-12)
  }

  test("income-weighted mean is total tax over total income") {
    val rate = IndexedSeq(0.1, 0.5); val n = IndexedSeq(10.0, 1.0); val y = IndexedSeq(100.0, 1000.0)
    assertEqualsDouble(incomeWeightedMean(rate, n, y), (0.1 * 1000 + 0.5 * 1000) / 2000, 1e-12)
  }

  test("excelRound rounds half away from zero") {
    assertEqualsDouble(excelRound(0.0445, 3), 0.045, 1e-15)
    assertEqualsDouble(excelRound(-0.0445, 3), -0.045, 1e-15)
    assertEqualsDouble(excelRound(0.0444999, 3), 0.044, 1e-15)
  }

  test("moving average replicates edges and preserves constants") {
    assertEquals(movingAverage(IndexedSeq.fill(5)(2.0), 3), IndexedSeq.fill(5)(2.0))
    val s = movingAverage(IndexedSeq(0.0, 0.0, 7.0), 1)
    assertEqualsDouble(s(0), 0.0, 1e-12)
    assertEqualsDouble(s(1), 7.0 / 3, 1e-12)
    assertEqualsDouble(s(2), 14.0 / 3, 1e-12) // window (0, 7, 7) with the last value replicated
  }
