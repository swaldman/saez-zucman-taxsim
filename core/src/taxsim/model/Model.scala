package taxsim.model

import taxsim.core.Numerics

/** One income group (e.g. a g-percentile). Groups are ranked by pre-tax income.
 *  @param percentile lower bound of the group, in percentiles; top-N lines use a value > 100
 *  @param income     average pre-tax income per adult: the denominator of all effective rates
 *  @param vars       other per-adult quantities and current effective rates, by name
 *  @param excess     threshold-excess curves by base name (e.g. "wealth", "taxableIncome") */
final case class Group(
    label: String,
    percentile: Double,
    adults: Double,
    income: Double,
    vars: Map[String, Double],
    excess: Map[String, ExcessCurve] = Map.empty
):
  def apply(v: String): Double =
    vars.getOrElse(v, throw new NoSuchElementException(s"group $label has no variable '$v'"))
  def getOrZero(v: String): Double = vars.getOrElse(v, 0.0)
  def excessOf(base: String): ExcessCurve = excess.getOrElse(base, ExcessCurve.zero)

/** Macro quantities used to convert shares of national income to money and to define budget windows. */
final case class Macro(
    currency: String,
    /** National income in `unit`s, consistent with the groups' total income. */
    nationalIncome: Double,
    unit: Double = 1e9,
    budgetGrowth: Double = 0.04,
    budgetWindowOffsetYears: Int = 2,
    budgetWindowYears: Int = 10
)

/** Broad category of a tax, for grouping and display. */
enum TaxCategory:
  case IncomeTax, Payroll, Consumption, Corporate, Property, Wealth, Estate, Health, Other

/** A country-year model: the population, its taxes and the levers that can change them. */
final case class Model(
    id: String,
    name: String,
    year: Int,
    groups: IndexedSeq[Group],
    instruments: IndexedSeq[Instrument],
    schema: LeverSchema,
    macroData: Macro,
    /** Model-level constants that instruments may look up (aggregates, incidence assumptions…). */
    constants: Map[String, Double] = Map.empty,
    /** The current tax system, as lever values. */
    currentPolicy: Policy = Policy.empty,
    /** Default model options (lever values in section "Options"). */
    defaultOptions: Policy = Policy.empty,
    /** Named example reforms. */
    presets: Map[String, Policy] = Map.empty,
    notes: String = ""
):
  require(instruments.map(_.id).distinct.length == instruments.length, "duplicate instrument ids")
  def instrument(id: String): Instrument =
    instruments.find(_.id == id).getOrElse(throw new NoSuchElementException(s"no instrument '$id'"))
  def constant(k: String): Double = constants.getOrElse(k, throw new NoSuchElementException(s"no model constant '$k'"))

/** Everything an instrument may consult while computing rates. Population aggregates and other
 *  instruments' proposed rates are computed lazily and memoized. */
final class Context(val model: Model, val current: Policy, val proposed: Policy, val options: Policy):
  val groups: IndexedSeq[Group] = model.groups
  private val adults = groups.map(_.adults)
  private val incomes = groups.map(_.income)
  private val aggCache = scala.collection.mutable.HashMap.empty[String, Double]
  private val rateCache = scala.collection.mutable.HashMap.empty[String, IndexedSeq[Double]]
  private val inProgress = scala.collection.mutable.HashSet.empty[String]

  /** Population-weighted mean of a variable: Σ n·v / Σ n. */
  def mean(v: String): Double =
    aggCache.getOrElseUpdate("mean:" + v, Numerics.mean(groups.map(_.getOrZero(v)), adults))

  /** Income-weighted mean of a rate variable: Σ n·y·v / Σ n·y. */
  def incomeWeightedMean(v: String): Double =
    aggCache.getOrElseUpdate("iwm:" + v, Numerics.incomeWeightedMean(groups.map(_.getOrZero(v)), adults, incomes))

  def totalAdults: Double = aggCache.getOrElseUpdate("adults", adults.sum)

  /** Memoized aggregate of anything else an instrument needs. */
  def cached(key: String)(compute: => Double): Double = aggCache.getOrElseUpdate(key, compute)

  /** Final proposed rates of another instrument (after its own crowd-out). */
  def proposedRates(id: String): IndexedSeq[Double] =
    rateCache.get(id) match
      case Some(r) => r
      case None =>
        if !inProgress.add(id) then throw new IllegalStateException(s"cyclic crowd-out involving '$id'")
        val ins = model.instrument(id)
        val r = groups.indices.map { g =>
          // Observed baselines already reflect the current broad taxes, so crowd-out is relative:
          // (1 − Σ proposed) / (1 − Σ baseline). With no current broad taxes this is (1 − Σ proposed).
          val crowdP = ins.crowdedOutBy.map(proposedRates(_)(g)).sum
          val crowdB = ins.crowdedOutBy.map(baselineRates(_)(g)).sum
          (1 - crowdP) / (1 - crowdB) * ins.reformed(this, g)
        }
        inProgress -= id
        rateCache(id) = r
        r

  def baselineRates(id: String): IndexedSeq[Double] =
    rateCache.getOrElseUpdate("baseline:" + id, { val ins = model.instrument(id); groups.indices.map(ins.baseline(this, _)) })

/** A tax. `baseline` is its current effective rate on group g (share of pre-tax income).
 *  `reformed` is its effective rate under the proposed system before crowd-out; the engine
 *  multiplies it by (1 − Σ proposed rates of `crowdedOutBy`) / (1 − Σ baseline rates of
 *  `crowdedOutBy`), which captures that broad taxes (e.g. a VAT or a national income tax) reduce
 *  the base of income-based taxes. */
trait Instrument:
  def id: String
  def label: String
  def category: TaxCategory
  def crowdedOutBy: Seq[String] = Nil
  /** Whether this instrument is counted at all under the given options (e.g. private health
   *  insurance shown as a tax or not). */
  def included(options: Policy): Boolean = true
  def baseline(ctx: Context, g: Int): Double
  def reformed(ctx: Context, g: Int): Double

/** Rates of all instruments for one group. */
final case class GroupOutcome(group: Group, baseline: Map[String, Double], proposed: Map[String, Double]):
  def baselineTotal: Double = baseline.values.sum
  def proposedTotal: Double = proposed.values.sum

final case class Outcome(model: Model, groups: IndexedSeq[GroupOutcome], instrumentIds: IndexedSeq[String]):
  private val n = groups.map(_.group.adults)
  private val y = groups.map(_.group.income)
  private def agg(f: GroupOutcome => Double) = Numerics.incomeWeightedMean(groups.map(f), n, y)

  def baselineAverageRate: Double = agg(_.baselineTotal)
  def proposedAverageRate: Double = agg(_.proposedTotal)
  def baselineAverageRate(id: String): Double = agg(_.baseline(id))
  def proposedAverageRate(id: String): Double = agg(_.proposed(id))
  /** Net revenue change as a share of national income. */
  def revenueShareOfIncome: Double = proposedAverageRate - baselineAverageRate
  /** Revenue change of one instrument as a share of national income. */
  def revenueShareOfIncome(id: String): Double = proposedAverageRate(id) - baselineAverageRate(id)
  /** Net revenue change in one year, in `macroData.unit`s of `macroData.currency`. */
  def annualRevenue: Double = revenueShareOfIncome * model.macroData.nationalIncome
  def budgetWindowRevenue: Double =
    val m = model.macroData
    val g = m.budgetGrowth
    annualRevenue * math.pow(1 + g, m.budgetWindowOffsetYears) * (math.pow(1 + g, m.budgetWindowYears) - 1) / g

object Engine:
  /** Runs `proposed` against the model's current policy. Levers missing from `proposed` keep their current values. */
  def run(model: Model, proposed: Policy, options: Policy = Policy.empty): Outcome =
    runAgainst(model, model.currentPolicy, model.currentPolicy ++ proposed, model.defaultOptions ++ options)

  def runAgainst(model: Model, current: Policy, proposed: Policy, options: Policy): Outcome =
    val ctx = Context(model, current, proposed, options)
    val active = model.instruments.filter(_.included(options))
    val baseline = active.map(i => i.id -> ctx.baselineRates(i.id)).toMap
    val reformed = active.map(i => i.id -> ctx.proposedRates(i.id)).toMap
    val outs = model.groups.indices.map { g =>
      GroupOutcome(model.groups(g), active.map(i => i.id -> baseline(i.id)(g)).toMap, active.map(i => i.id -> reformed(i.id)(g)).toMap)
    }
    Outcome(model, outs, active.map(_.id))
