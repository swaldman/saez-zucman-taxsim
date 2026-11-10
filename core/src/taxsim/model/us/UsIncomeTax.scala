package taxsim.model.us

import taxsim.model.*

/** US individual income tax as modeled by Saez & Zucman (calculatorbig BB..CI, W).
 *
 *  rate = max(observed rate + (CH + CI)/income, 0), where
 *   - CI is the extra tax from moving the bracket schedule (computed on taxable-income excess
 *     curves, so thresholds may change), applied to ordinary income only unless dividends and/or
 *     capital gains are made fully taxable. Dividend–corporate integration grosses income up by
 *     the corporate tax on dividends and on retained earnings of closely held C-corporations.
 *   - CH is the extra tax from taxing capital gains / dividends at ordinary rates under the
 *     current schedule (using the average marginal rate above 20%), or, with integration, the
 *     extra tax on grossed-up dividends and closely held retained earnings net of the corporate tax credit.
 *  The switches mean "different from current law", as in the workbook.
 *
 *  @param corporate the corporate instrument, whose rates define the federal corporate rate used for integration
 *  @param currentFederalCorporateTax current federal corporate tax on domestic profits (same unit as `corporate.profits`)
 *  @param closelyHeldRetainedEarnings retained earnings of closely held C-corps, in units of `aggregateUnit`
 */
final case class UsIncomeTax(
    id: String,
    label: String,
    rateVar: String,
    base: String,
    scheduleLever: String,
    capitalGainsFullLever: String,
    dividendsFullLever: String,
    integrationLever: String,
    corporate: Instruments.CorporateTax,
    currentFederalCorporateTax: Double,
    closelyHeldRetainedEarnings: Double,
    aggregateUnit: Double,
    preferentialRate: Double = 0.2,
    capitalGainsVar: String = "capitalGains",
    dividendsVar: String = "dividends",
    fiscalIncomeVar: String = "fiscalIncome",
    marginalRateVar: String = "marginalRate",
    marginalRateAbove20Var: String = "marginalRateAbove20",
    override val crowdedOutBy: Seq[String] = Nil
) extends Instrument:
  val category: TaxCategory = TaxCategory.IncomeTax

  def baseline(ctx: Context, g: Int): Double = ctx.groups(g)(rateVar)

  def reformed(ctx: Context, g: Int): Double =
    val grp = ctx.groups(g)
    val (c, p) = (ctx.current, ctx.proposed)
    val d = c.flag(dividendsFullLever) != p.flag(dividendsFullLever)
    val i = c.flag(integrationLever) != p.flag(integrationLever)
    val k = c.flag(capitalGainsFullLever) != p.flag(capitalGainsFullLever)

    val profits = corporate.profits
    val curForeign = corporate.foreignTaxCurrent / profits
    val curFed = currentFederalCorporateTax / profits + curForeign
    val newFed = p.num(corporate.effectiveRateLever) - (c.num(corporate.effectiveRateLever) - curFed) + corporate.foreignTax(ctx) / profits

    val kg = grp(capitalGainsVar)
    val div = grp(dividendsVar)
    val agi = grp(fiscalIncomeVar)
    val mtr = grp(marginalRateVar)
    val mtrx = grp(marginalRateAbove20Var)
    val avgRetained = closelyHeldRetainedEarnings * aggregateUnit / ctx.totalAdults
    val retained = (kg + div) * avgRetained / (ctx.mean(capitalGainsVar) + ctx.mean(dividendsVar))
    val integrated = div * ((mtr - newFed) / (1 - curFed) - math.min(preferentialRate, mtr)) + retained * (mtr - newFed) / (1 - curFed)
    val sKg = kg / agi
    val sDiv = div / agi
    val grossUp = div * curFed / (1 - curFed) + retained / (1 - curFed)

    val curve = grp.excessOf(base)
    val dB = p.schedule(scheduleLever).averageTax(curve.at) - c.schedule(scheduleLever).averageTax(curve.at)
    val bracketPart =
      if !i && !d && !k then (1 - sKg - sDiv) * dB
      else if !i && d && !k then (1 - sKg) * dB
      else if !i && !d && k then (1 - sDiv) * dB
      else if !i && d && k then dB
      else if k then dB * (1 + grossUp / agi)
      else dB * (1 + grossUp / agi - sKg)
    val preferencePart =
      (if i then integrated else 0.0) + (if d && !i then div * mtrx else 0.0) + (if k then kg * mtrx else 0.0)
    math.max(grp(rateVar) + (preferencePart + bracketPart) / grp.income, 0.0)
