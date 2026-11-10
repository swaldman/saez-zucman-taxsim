package taxsim.model

/** A piecewise-linear marginal-rate schedule: `rates(k)` applies to the part of the base above
 *  `thresholds(k)` (and below `thresholds(k+1)`). Thresholds are strictly increasing. */
final case class Schedule(thresholds: IndexedSeq[Double], rates: IndexedSeq[Double]):
  require(thresholds.length == rates.length, s"${thresholds.length} thresholds but ${rates.length} rates")
  require(thresholds.indices.drop(1).forall(k => thresholds(k) > thresholds(k - 1)), s"thresholds must increase: $thresholds")

  /** Average tax for a group whose average excess over threshold t is `excess(t)`:
   *  Σ_k (r_k − r_{k−1}) · excess(t_k). Exact for any distribution within the group. */
  def averageTax(excess: Double => Double): Double =
    var s = 0.0
    var prev = 0.0
    var k = 0
    while k < rates.length do
      val dr = rates(k) - prev
      if dr != 0.0 then s += dr * excess(thresholds(k))
      prev = rates(k)
      k += 1
    s

  /** Marginal rate applying at base level x. */
  def rateAt(x: Double): Double =
    val k = thresholds.lastIndexWhere(_ <= x)
    if k < 0 then 0.0 else rates(k)

  /** Splits the bracket containing `t` at `t` (no change in tax). */
  def insertThreshold(t: Double): Schedule =
    if thresholds.contains(t) then this
    else
      val k = thresholds.indexWhere(_ > t)
      val at = if k < 0 then thresholds.length else k
      Schedule(thresholds.patch(at, Seq(t), 0), rates.patch(at, Seq(rateAt(t)), 0))

  /** Raises every marginal rate above `t` to at least `r` (e.g. "a 50% top bracket above $3m"). */
  def withMinimumRateAbove(t: Double, r: Double): Schedule =
    val s = insertThreshold(t)
    s.copy(rates = s.rates.zip(s.thresholds).map((rate, th) => if th >= t then math.max(rate, r) else rate))

  def withRates(rs: IndexedSeq[Double]): Schedule = copy(rates = rs)
  def scaled(f: Double): Schedule = copy(rates = rates.map(_ * f))

  /** Adds a fixed surcharge to every bracket (`first` for the first bracket, `others` above). */
  def plusSurcharge(first: Double, others: Double): Schedule =
    copy(rates = rates.zipWithIndex.map((r, k) => r + (if k == 0 then first else others)))

object Schedule:
  val empty: Schedule = Schedule(IndexedSeq(0.0), IndexedSeq(0.0))
  def flat(rate: Double): Schedule = Schedule(IndexedSeq(0.0), IndexedSeq(rate))

/** The value of one policy lever. */
enum LeverValue:
  case Num(value: Double)
  case Flag(value: Boolean)
  case Sched(value: Schedule)

/** A tax system (or a set of model options), as values of named levers. */
final case class Policy(values: Map[String, LeverValue]):
  def get(id: String): Option[LeverValue] = values.get(id)

  def num(id: String): Double = values.get(id) match
    case Some(LeverValue.Num(v)) => v
    case other                   => throw new NoSuchElementException(s"lever '$id' is not a number: $other")

  def flag(id: String): Boolean = values.get(id) match
    case Some(LeverValue.Flag(v)) => v
    case other                    => throw new NoSuchElementException(s"lever '$id' is not a flag: $other")

  def schedule(id: String): Schedule = values.get(id) match
    case Some(LeverValue.Sched(v)) => v
    case other                     => throw new NoSuchElementException(s"lever '$id' is not a schedule: $other")

  def set(id: String, v: Double): Policy = Policy(values.updated(id, LeverValue.Num(v)))
  def set(id: String, v: Boolean): Policy = Policy(values.updated(id, LeverValue.Flag(v)))
  def set(id: String, v: Schedule): Policy = Policy(values.updated(id, LeverValue.Sched(v)))
  def ++(other: Policy): Policy = Policy(values ++ other.values)

object Policy:
  val empty: Policy = Policy(Map.empty)
  def apply(entries: (String, LeverValue)*): Policy = Policy(entries.toMap)

/** UI/validation description of a lever. */
enum LeverKind:
  /** A number in [min, max]; `percent` = display as a percentage. */
  case Slider(min: Double, max: Double, step: Double, percent: Boolean = true)
  case Toggle
  /** A bracket schedule. If `fixedThresholds`, users may change rates only. */
  case Brackets(maxRate: Double, fixedThresholds: Boolean = false)

final case class LeverSpec(id: String, label: String, section: String, kind: LeverKind, help: String = "")

/** The levers a model exposes, in display order. Model options (not tax levers) use section "Options". */
final case class LeverSchema(levers: IndexedSeq[LeverSpec]):
  private val byId = levers.map(l => l.id -> l).toMap
  def apply(id: String): LeverSpec = byId(id)
  def contains(id: String): Boolean = byId.contains(id)

  /** Problems with a policy: unknown levers, wrong value kinds, out-of-range values. Empty = valid. */
  def validate(p: Policy): Seq[String] =
    val unknown = p.values.keySet.diff(byId.keySet).toSeq.sorted.map(id => s"unknown lever '$id'")
    val bad = p.values.toSeq.sortBy(_._1).flatMap { (id, v) =>
      byId.get(id).toSeq.flatMap { spec =>
        (spec.kind, v) match
          case (LeverKind.Slider(lo, hi, _, _), LeverValue.Num(x)) =>
            if x < lo - 1e-12 || x > hi + 1e-12 then Seq(s"'$id' = $x outside [$lo, $hi]") else Nil
          case (LeverKind.Toggle, LeverValue.Flag(_)) => Nil
          case (LeverKind.Brackets(maxRate, _), LeverValue.Sched(s)) =>
            s.rates.filter(r => r < 0 || r > maxRate).map(r => s"'$id' rate $r outside [0, $maxRate]")
          case (k, v) => Seq(s"'$id' expects $k but got $v")
      }
    }
    unknown ++ bad
