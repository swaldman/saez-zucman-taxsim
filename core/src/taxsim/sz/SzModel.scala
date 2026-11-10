package taxsim.sz

import taxsim.core.Numerics

/** Effective tax rates of one group, by tax, under the proposed system. All are shares of pre-tax income.
 *  Spreadsheet columns (sheet `calculatorbig`) in brackets. */
final case class SzRates(
    wealth: Double,          // [Q]
    nationalIncome: Double,  // [R]
    vat: Double,             // [S]
    healthInsurance: Double, // [T]
    estate: Double,          // [U]
    corporate: Double,       // [V]
    incomeTax: Double,       // [W]
    sales: Double,           // [X]
    payroll: Double,         // [Y]
    property: Double         // [I]
):
  def total: Double = property + payroll + wealth + nationalIncome + vat + healthInsurance + estate + corporate + incomeTax + sales

/** Per-group output. `currentRate` is the total rate under the current system [E];
 *  `proposed` decomposes the total rate under the proposed system [Z]. */
final case class SzGroupResult(group: SzGroup, currentRate: Double, proposed: SzRates):
  def proposedRate: Double = proposed.total

/** Aggregate output. Rates are income-weighted means (total tax / total income). */
final case class SzResult(groups: IndexedSeq[SzGroupResult], params: SzParams):
  private def n = groups.map(_.group.adults)
  private def y = groups.map(_.group.income)
  private def agg(f: SzGroupResult => Double): Double = Numerics.incomeWeightedMean(groups.map(f), n, y)

  /** Average tax rate on all income under the current system [E137]. */
  def currentAverageRate: Double = agg(_.currentRate)
  /** Average tax rate on all income under the proposed system [Z137]. */
  def proposedAverageRate: Double = agg(_.proposedRate)
  /** Average proposed rate by tax [Q137..Y137]. */
  def proposedAverageRates: SzRates =
    SzRates(agg(_.proposed.wealth), agg(_.proposed.nationalIncome), agg(_.proposed.vat), agg(_.proposed.healthInsurance),
      agg(_.proposed.estate), agg(_.proposed.corporate), agg(_.proposed.incomeTax), agg(_.proposed.sales),
      agg(_.proposed.payroll), agg(_.proposed.property))
  /** Net revenue change as a share of national income [Z138]. */
  def revenueShareOfIncome: Double = proposedAverageRate - currentAverageRate
  /** Net revenue change in one year, in `params.aggregateUnit`s (interface X8). */
  def annualRevenue: Double = revenueShareOfIncome * params.nationalIncome
  /** Net revenue change over the budget window (interface Y8). */
  def budgetWindowRevenue: Double =
    val g = params.budgetGrowth
    annualRevenue * math.pow(1 + g, params.budgetWindowOffsetYears) * (math.pow(1 + g, params.budgetWindowYears) - 1) / g

/** The static tax-incidence model of the Saez-Zucman tax simulator (taxjusticenow.org), a
 *  faithful port of sheet `calculatorbig` of `taxsimulator2.xlsx` (January 2020).
 *
 *  The model takes the current incidence of each tax by income group and recomputes each tax
 *  under the proposed system: new taxes (wealth, national income, VAT) are computed from their
 *  bases, existing taxes are rescaled. See `docs/methodology.md` for the full derivation.
 */
object SzModel:

  def run(
      groups: IndexedSeq[SzGroup],
      current: SzPolicy,
      proposed: SzPolicy,
      options: SzOptions = SzOptions(),
      params: SzParams = SzParams()
  ): SzResult =
    val n = groups.map(_.adults)
    val y = groups.map(_.income)
    val p = params

    // ---- Aggregates over all groups (row 137)
    val totalAdults = n.sum
    val avgSalesRate = Numerics.incomeWeightedMean(groups.map(_.salesTaxRate), n, y)        // F137
    val avgCapitalGains = Numerics.mean(groups.map(_.capitalGains), n)                     // BC137
    val avgDividends = Numerics.mean(groups.map(_.dividends), n)                           // BD137
    val avgClosedRetained = p.closelyHeldRetainedEarnings * p.aggregateUnit / totalAdults  // BB137

    // ---- Corporate tax (calculatorbig Q141..R144)
    val profits = p.corporateProfits
    val curForeign = p.currentForeignProfitsTax / profits                                   // Q143
    val newForeign =
      if current.betterMultinationalTaxation == proposed.betterMultinationalTaxation then curForeign
      else p.reformedForeignProfitsTax / profits                                            // R143
    val curFedCorpRate = p.currentFederalCorporateTax / profits + curForeign                // Q142
    val newFedCorpRate = proposed.corporateEffectiveRate - (current.corporateEffectiveRate - curFedCorpRate) + newForeign // R142
    val curCorpTotal = current.corporateEffectiveRate * profits + curForeign * profits      // Q144
    val newCorpTotal = proposed.corporateEffectiveRate * profits + newForeign * profits     // R144

    // ---- Individual income tax switches (Z141..AA143): 1 when the proposed treatment differs from current
    val divSwitch = current.dividendsFullyTaxed != proposed.dividendsFullyTaxed                    // AA141
    val integrationSwitch = current.dividendCorporateIntegration != proposed.dividendCorporateIntegration // AA142
    val kgSwitch = current.capitalGainsFullyTaxed != proposed.capitalGainsFullyTaxed              // AA143
    val bracketDelta = proposed.incomeTaxRates.lazyZip(current.incomeTaxRates).map(_ - _)

    // ---- Wealth tax rates incl. financial-transaction-tax surcharge (G143..G156)
    val wealthRates = proposed.wealthTaxRates.zipWithIndex.map { (r, k) =>
      if !proposed.financialTransactionTax then r
      else r + (if k == 0 then p.fttWealthSurchargeFirstBracket else p.fttWealthSurcharge)
    }

    val results = groups.map { g =>
      val inc = g.income
      // Private health insurance shown as a tax only when the option is on [L]
      val health = if options.healthInsuranceAsTax then g.healthInsuranceRate else 0.0

      // [Q] progressive wealth tax
      val wealthTax = (1 - proposed.wealthTaxEvasion) * Numerics.bracketTax(wealthRates, padTo(g.wealthExcess, wealthRates.length)) / inc
      // [R] flat national income tax
      val nit = proposed.nationalIncomeTaxRate * g.nationalIncomeTaxBase / inc
      // [S] VAT: tax-exclusive rate v ⇒ tax-inclusive share v/(1+v) of a base equal to a share of income,
      //     distributed partly like current sales taxes and partly proportionally to income.
      val vatShare = proposed.vatRate / (1 + proposed.vatRate)
      val vat = vatShare * p.vatBaseShareOfIncome *
        (p.vatConsumptionProfileShare * g.salesTaxRate / avgSalesRate + (1 - p.vatConsumptionProfileShare))
      // [T] private health insurance, reduced by public replacement
      val healthTax = health * (1 - proposed.privateHealthReduction)
      // [U] estate tax rescaled by the top rate and by enforcement
      val estate = (proposed.estateTaxTopRate * g.estateTaxRate / p.estateBaselineTopRate) *
        (1 - proposed.estateTaxAvoidance) / (1 - p.estateBaselineAvoidance)
      // New broad taxes (NIT, VAT) crowd out the bases of the corporate and income taxes.
      val crowdOut = 1 - nit - vat
      // [V] corporate tax rescaled by total corporate tax collected
      val corporate = crowdOut * g.corporateTaxRate * newCorpTotal / curCorpTotal

      // [W] individual income tax
      val closedRetained = (g.capitalGains + g.dividends) * avgClosedRetained / (avgCapitalGains + avgDividends) // BB
      val kgExtraCurrent = g.capitalGains * g.marginalRateAbove20                                               // BU
      val divExtraCurrent = g.dividends * g.marginalRateAbove20                                                 // BV
      val integratedExtra =                                                                                     // BW
        g.dividends * ((g.marginalRate - newFedCorpRate) / (1 - curFedCorpRate) - math.min(p.preferentialRate, g.marginalRate)) +
          closedRetained * (g.marginalRate - newFedCorpRate) / (1 - curFedCorpRate)
      val kgShare = g.capitalGains / g.fiscalIncome                                                             // BX
      val divShare = g.dividends / g.fiscalIncome                                                               // BY
      val grossUp = g.dividends * curFedCorpRate / (1 - curFedCorpRate) + closedRetained / (1 - curFedCorpRate) // CA
      val bracketExtra = Numerics.bracketTax(bracketDelta, padTo(g.taxableIncomeExcess, bracketDelta.length))   // CE
      val newBracketExtra =                                                                                     // CI (CB..CG)
        if !integrationSwitch && !divSwitch && !kgSwitch then (1 - kgShare - divShare) * bracketExtra
        else if !integrationSwitch && divSwitch && !kgSwitch then (1 - kgShare) * bracketExtra
        else if !integrationSwitch && !divSwitch && kgSwitch then (1 - divShare) * bracketExtra
        else if !integrationSwitch && divSwitch && kgSwitch then bracketExtra
        else if integrationSwitch && kgSwitch then bracketExtra * (1 + grossUp / g.fiscalIncome)
        else bracketExtra * (1 + grossUp / g.fiscalIncome - kgShare)
      val preferenceExtra =                                                                                     // CH
        (if integrationSwitch then integratedExtra else 0.0) +
          (if divSwitch && !integrationSwitch then divExtraCurrent else 0.0) +
          (if kgSwitch then kgExtraCurrent else 0.0)
      val incomeTax = crowdOut * math.max(g.incomeTaxRate + (preferenceExtra + newBracketExtra) / inc, 0.0)

      // [X] sales taxes, [Y] payroll taxes (+ optional employer head tax replacing health contributions)
      val sales = g.salesTaxRate * (1 - proposed.salesTaxReduction)
      val payroll = g.payrollTaxRate + (if proposed.healthHeadTax then p.healthHeadTaxShare * g.healthInsuranceRate else 0.0)

      SzGroupResult(
        g,
        currentRate = g.baselineTotalRate + health,
        proposed = SzRates(wealthTax, nit, vat, healthTax, estate, corporate, incomeTax, sales, payroll, g.propertyTaxRate)
      )
    }
    SzResult(results, params)

  private def padTo(xs: IndexedSeq[Double], n: Int): IndexedSeq[Double] =
    if xs.length >= n then xs.take(n) else xs ++ IndexedSeq.fill(n - xs.length)(0.0)
