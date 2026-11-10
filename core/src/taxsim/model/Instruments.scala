package taxsim.model

/** Reusable tax instruments. Each is plain data plus a rate rule, so models for different
 *  countries are assembled by configuration rather than new engine code.
 *
 *  Conventions: `rateVar` names a group variable holding the current effective rate (share of
 *  pre-tax income); lever ids refer to the model's [[LeverSchema]]; `ctx.current` and
 *  `ctx.proposed` are the two policies being compared. */
object Instruments:

  private def ratio(num: Double, den: Double): Double = if den == 0.0 then (if num == 0.0 then 1.0 else Double.NaN) else num / den

  /** A tax kept at its current incidence (e.g. property taxes in the US model). */
  final case class Fixed(id: String, label: String, category: TaxCategory, rateVar: String) extends Instrument:
    def baseline(ctx: Context, g: Int): Double = ctx.groups(g)(rateVar)
    def reformed(ctx: Context, g: Int): Double = baseline(ctx, g)

  /** A tax whose incidence is scaled by (1 − reduction): e.g. "cut sales taxes by 50%", or
   *  "replace x% of private health insurance by public funding". When `shownIf` names an
   *  option lever, the tax is only counted when that option is on. */
  final case class Reducible(
      id: String,
      label: String,
      category: TaxCategory,
      rateVar: String,
      reductionLever: String,
      shownIf: Option[String] = None,
      override val crowdedOutBy: Seq[String] = Nil
  ) extends Instrument:
    override def included(options: Policy): Boolean = shownIf.forall(options.flag)
    def baseline(ctx: Context, g: Int): Double = ctx.groups(g)(rateVar)
    def reformed(ctx: Context, g: Int): Double =
      baseline(ctx, g) * ratio(1 - ctx.proposed.num(reductionLever), 1 - ctx.current.num(reductionLever))

  /** A tax whose incidence scales with a statutory top rate and with compliance (1 − avoidance),
   *  relative to current values. The US estate tax in Saez-Zucman. */
  final case class RateAndAvoidanceScaled(
      id: String,
      label: String,
      category: TaxCategory,
      rateVar: String,
      topRateLever: String,
      avoidanceLever: String
  ) extends Instrument:
    def baseline(ctx: Context, g: Int): Double = ctx.groups(g)(rateVar)
    def reformed(ctx: Context, g: Int): Double =
      val (c, p) = (ctx.current, ctx.proposed)
      baseline(ctx, g) * ratio(p.num(topRateLever), c.num(topRateLever)) *
        ratio(1 - p.num(avoidanceLever), 1 - c.num(avoidanceLever))

  /** Corporate income tax allocated to equity owners and rescaled by total revenue:
   *  K = profits · (effective rate on domestic profits + tax on foreign profits / profits).
   *  The foreign-profits toggle switches between `foreignTaxCurrent` and `foreignTaxReformed`
   *  when it differs from current law. */
  final case class CorporateTax(
      id: String,
      label: String,
      rateVar: String,
      effectiveRateLever: String,
      foreignToggle: String,
      profits: Double,
      foreignTaxCurrent: Double,
      foreignTaxReformed: Double,
      override val crowdedOutBy: Seq[String] = Nil
  ) extends Instrument:
    val category: TaxCategory = TaxCategory.Corporate
    def foreignTax(ctx: Context): Double =
      if ctx.proposed.flag(foreignToggle) == ctx.current.flag(foreignToggle) then foreignTaxCurrent else foreignTaxReformed
    def baseline(ctx: Context, g: Int): Double = ctx.groups(g)(rateVar)
    def reformed(ctx: Context, g: Int): Double =
      val kc = ctx.current.num(effectiveRateLever) * profits + foreignTaxCurrent
      val kp = ctx.proposed.num(effectiveRateLever) * profits + foreignTax(ctx)
      baseline(ctx, g) * kp / kc

  /** Payroll taxes / social contributions, kept at current incidence, plus an optional
   *  employer head tax equal to `headTaxShare` × a per-group rate variable (in the US model,
   *  a head tax replacing employers' health contributions). */
  final case class Payroll(
      id: String,
      label: String,
      rateVar: String,
      headTaxToggle: Option[String] = None,
      headTaxRateVar: String = "",
      headTaxShare: Double = 0.0
  ) extends Instrument:
    val category: TaxCategory = TaxCategory.Payroll
    private def head(p: Policy, g: Group): Double =
      headTaxToggle match
        case Some(t) if p.flag(t) => headTaxShare * g(headTaxRateVar)
        case _                    => 0.0
    def baseline(ctx: Context, g: Int): Double = ctx.groups(g)(rateVar) // observed rate already reflects current law
    def reformed(ctx: Context, g: Int): Double =
      val grp = ctx.groups(g)
      grp(rateVar) + head(ctx.proposed, grp) - head(ctx.current, grp)

  /** A flat tax on a per-adult base variable (e.g. a national income tax on all income). */
  final case class FlatTax(
      id: String,
      label: String,
      category: TaxCategory,
      baseVar: String,
      rateLever: String,
      baselineVar: Option[String] = None,
      override val crowdedOutBy: Seq[String] = Nil
  ) extends Instrument:
    private def computed(p: Policy, g: Group): Double = p.num(rateLever) * g(baseVar) / g.income
    def baseline(ctx: Context, g: Int): Double =
      val grp = ctx.groups(g)
      baselineVar.fold(computed(ctx.current, grp))(grp(_))
    def reformed(ctx: Context, g: Int): Double =
      val grp = ctx.groups(g)
      baseline(ctx, g) + computed(ctx.proposed, grp) - computed(ctx.current, grp)

  /** A progressive tax on a base described by per-group [[ExcessCurve]]s: wealth taxes,
   *  income-tax schedules, etc. Users may move thresholds freely; the excess curves interpolate.
   *
   *  The amount collected is compliance × schedule.averageTax / income, where compliance =
   *  1 − evasion. The tax is computed by difference: reformed = baseline + T(proposed) − T(current),
   *  so a calibrated observed baseline (`baselineVar`) is preserved for a zero reform.
   *  `fixedSchedule` replaces the lever, for taxes whose schedule is not user-editable (e.g. a
   *  financial transaction tax modeled as a wealth-tax surcharge, switched by `enabledLever`). */
  final case class ScheduleTax(
      id: String,
      label: String,
      category: TaxCategory,
      base: String,
      scheduleLever: Option[String],
      fixedSchedule: Option[Schedule] = None,
      evasionLever: Option[String] = None,
      enabledLever: Option[String] = None,
      baselineVar: Option[String] = None,
      override val crowdedOutBy: Seq[String] = Nil
  ) extends Instrument:
    require(scheduleLever.isDefined != fixedSchedule.isDefined, s"$id: exactly one of scheduleLever / fixedSchedule")
    def schedule(p: Policy): Schedule = scheduleLever.fold(fixedSchedule.get)(p.schedule)
    def collected(p: Policy, g: Group): Double =
      if !enabledLever.forall(p.flag) then 0.0
      else
        val compliance = evasionLever.fold(1.0)(e => 1 - p.num(e))
        compliance * schedule(p).averageTax(g.excessOf(base).at) / g.income
    def baseline(ctx: Context, g: Int): Double =
      val grp = ctx.groups(g)
      baselineVar.fold(collected(ctx.current, grp))(grp(_))
    def reformed(ctx: Context, g: Int): Double =
      val grp = ctx.groups(g)
      baseline(ctx, g) + collected(ctx.proposed, grp) - collected(ctx.current, grp)

  /** A broad consumption tax (VAT/GST). With a tax-exclusive standard rate v, the tax is a share
   *  v/(1+v) of a consumption base equal to `baseShareOfIncome` of national income. That base is
   *  distributed across groups partly (`consumerShare`) following a consumption profile (the
   *  per-group rate variable `profileVar`, normalized to an income-weighted mean of 1) and partly
   *  in proportion to income (the factor-incidence share).
   *
   *  If the country has a VAT and `baselineVar` gives its observed incidence, a rate change
   *  rescales the observed incidence by (v_p/(1+v_p)) / (v_c/(1+v_c)); otherwise the formula is used.
   *  Saez-Zucman use baseShareOfIncome = 0.4, consumerShare = 0.75 and the US sales-tax profile. */
  final case class ConsumptionTax(
      id: String,
      label: String,
      rateLever: String,
      profileVar: String,
      baseShareOfIncome: Double,
      consumerShare: Double,
      baselineVar: Option[String] = None,
      override val crowdedOutBy: Seq[String] = Nil
  ) extends Instrument:
    val category: TaxCategory = TaxCategory.Consumption
    private def inclusive(v: Double) = v / (1 + v)
    def formula(ctx: Context, v: Double, g: Int): Double =
      val profile = ctx.groups(g)(profileVar) / ctx.incomeWeightedMean(profileVar)
      inclusive(v) * baseShareOfIncome * (consumerShare * profile + (1 - consumerShare))
    def baseline(ctx: Context, g: Int): Double =
      baselineVar.fold(formula(ctx, ctx.current.num(rateLever), g))(ctx.groups(g)(_))
    def reformed(ctx: Context, g: Int): Double =
      val (vc, vp) = (ctx.current.num(rateLever), ctx.proposed.num(rateLever))
      if baselineVar.isDefined && vc > 0 then baseline(ctx, g) * inclusive(vp) / inclusive(vc)
      else baseline(ctx, g) + formula(ctx, vp, g) - formula(ctx, vc, g)
