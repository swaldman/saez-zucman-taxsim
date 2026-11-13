package taxsim.parity

import taxsim.data.sz2019.Sz2019

/** Checks the claims of `docs/methodology.md` §1, "Two income concepts", against the 2018 data:
 *  groups are ranked by `avg` (pre-tax income), `thres` are cutoffs of `avg`, and
 *  `denom` = `avg` + 0.366 × realized capital gains in every group. */
class IncomeConceptsTest extends munit.FunSuite:
  private val csv = Sz2019.databig
  private val avg = csv.doubles("avg")
  private val denom = csv.doubles("denom")
  private val thres = csv.doubles("thres")
  private val kg = csv.doubles("fikgi")
  private val n = avg.length

  test("avg lies within its group's threshold band (thresholds are rounded to 3 significant figures)") {
    for i <- 0 until n do
      assert(avg(i) >= thres(i) * (1 - 5e-3), s"group $i: avg ${avg(i)} below threshold ${thres(i)}")
      if i + 1 < n then assert(avg(i) < thres(i + 1), s"group $i: avg ${avg(i)} above next threshold ${thres(i + 1)}")
  }

  test("denom is not the ranking variable: it exceeds the next threshold in some top groups") {
    assert((0 until n - 1).count(i => denom(i) >= thres(i + 1)) >= 10)
  }

  test("denom − avg = 0.366 × realized capital gains in every group with gains above $1,000 per adult") {
    val ratios = (0 until n).filter(i => math.abs(kg(i)) > 1000).map(i => (denom(i) - avg(i)) / kg(i))
    assert(ratios.size >= 40, s"only ${ratios.size} groups")
    ratios.foreach(r => assertEqualsDouble(r, 0.366, 1e-3))
  }

  test("the model's groups carry both concepts") {
    val g = Sz2019.model.groups.last
    assertEqualsDouble(g.income, denom.last, 0.0)
    assertEqualsDouble(g.vars("pretaxIncome"), avg.last, 0.0)
  }
