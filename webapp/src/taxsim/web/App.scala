package taxsim.web

import taxsim.data.sz2019.Sz2019
import taxsim.data.us.UsModel
import taxsim.model.*

/** The model and derived, display-oriented helpers shared by both pages. */
object App:
  val model: Model = Sz2019.model
  val groups: IndexedSeq[Group] = model.groups
  val xs: IndexedSeq[Double] = groups.indices.map(_.toDouble)
  val ticks: Seq[(Double, String)] = Format.groupTicks(groups)
  val labels: IndexedSeq[String] = groups.indices.map(Format.groupLabel(groups, _))
  def groupSub(i: Int): String = s"Average pre-tax income ${Format.amount(groups(i).income)}"

  final case class Plan(key: String, label: String, cls: String)
  /** The plans of the workbook's figure, in a fixed order; each keeps its color everywhere. */
  val figurePlans: Seq[Plan] = Seq(
    Plan("current", "Current law (2018)", "series-1"),
    Plan("obama", "Obama (repeal 2017 tax cut)", "series-2"),
    Plan("biden", "Biden", "series-3"),
    Plan("buttigieg", "Buttigieg", "series-4"),
    Plan("warren", "Warren", "series-5"),
    Plan("sanders", "Sanders", "series-6")
  )
  val bookPlan: Plan = Plan("book", "Saez–Zucman book plan", "series-7")
  val allPlans: Seq[Plan] = figurePlans :+ bookPlan
  def planLabel(key: String): String = allPlans.find(_.key == key).map(_.label).getOrElse(key)

  def options(health: Boolean): Policy = UsModel.optionsOf(Sz2019.options.copy(healthInsuranceAsTax = health))

  def run(policy: Policy, health: Boolean): Outcome = Engine.run(model, policy, options(health))

  def behavioral(out: Outcome): BehavioralResult =
    Behavioral.run(
      Behavioral.fromOutcome(out, Sz2019.growth.counterfactualIncome, Sz2019.growth.missingIncomeRaise),
      UsModel.behavioralSettings(Sz2019.options, Sz2019.params))

  /** Income-weighted average rate over groups selected by percentile. */
  def groupRate(out: Outcome, proposed: Boolean, which: Group => Boolean): Double =
    val gs = out.groups.filter(o => which(o.group))
    val w = gs.map(o => o.group.adults * o.group.income)
    gs.lazyZip(w).map((o, wi) => (if proposed then o.proposedTotal else o.baselineTotal) * wi).sum / w.sum

  val bottom50: Group => Boolean = _.percentile < 50
  val bottom90: Group => Boolean = _.percentile < 90
  val top01: Group => Boolean = _.percentile >= 99.9 - 1e-9
  val top400: Group => Boolean = _.percentile > 100

  /** Tax categories for breakdown charts: at most 8, in a fixed order with fixed colors. */
  final case class Category(label: String, cls: String, ids: Seq[String])
  val categories: Seq[Category] = Seq(
    Category("Individual income tax", "series-1", Seq("incomeTax")),
    Category("Payroll taxes", "series-2", Seq("payroll")),
    Category("Sales & excise taxes", "series-3", Seq("sales")),
    Category("Corporate tax", "series-4", Seq("corporate")),
    Category("Property taxes", "series-5", Seq("property")),
    Category("Wealth & estate taxes", "series-6", Seq("wealth", "ftt", "estate")),
    Category("New broad taxes (NIT, VAT)", "series-7", Seq("nit", "vat")),
    Category("Private health insurance", "series-8", Seq("health"))
  )

  def breakdown(out: Outcome, proposed: Boolean): Seq[Series] =
    categories.flatMap { c =>
      val vs = out.groups.map(o => c.ids.map(id => (if proposed then o.proposed else o.baseline).getOrElse(id, 0.0)).sum)
      if vs.forall(v => math.abs(v) < 1e-12) then None else Some(Series(c.label, c.cls, vs))
    }

  /** A breakdown with a leading "Total tax" column (the height of the stack), for data tables. */
  def withTotal(parts: Seq[Series]): Seq[Series] =
    Series("Total tax", "", xs.indices.map(i => parts.map(_.values(i)).sum)) +: parts
