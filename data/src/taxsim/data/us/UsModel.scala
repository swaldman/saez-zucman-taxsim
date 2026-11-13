package taxsim.data.us

import taxsim.model.*
import taxsim.model.Instruments.*
import taxsim.model.LeverKind.*
import taxsim.model.us.UsIncomeTax
import taxsim.sz.{SzGroup, SzOptions, SzParams, SzPolicy}

/** The Saez-Zucman US model expressed with the generic building blocks of [[taxsim.model]].
 *  [[build]] works for any dataset in the `SzGroup` format, so a refreshed US dataset (another year)
 *  only needs new groups, parameters and presets. */
object UsModel:

  /** Lever ids (stable: used in saved plans and URLs). */
  object L:
    val nitRate = "nit.rate"
    val vatRate = "vat.rate"
    val salesReduction = "sales.reduction"
    val healthReduction = "health.reduction"
    val healthHeadTax = "health.headTax"
    val wealthEvasion = "wealth.evasion"
    val wealthSchedule = "wealth.schedule"
    val ftt = "ftt.enabled"
    val estateAvoidance = "estate.avoidance"
    val estateTopRate = "estate.topRate"
    val corpRate = "corp.rate"
    val corpMultinationals = "corp.multinationals"
    val pitCapitalGains = "pit.capitalGainsFull"
    val pitDividends = "pit.dividendsFull"
    val pitIntegration = "pit.integration"
    val pitSchedule = "pit.schedule"
    val healthAsTax = "options.healthAsTax"

  val schema: LeverSchema = LeverSchema(IndexedSeq(
    LeverSpec(L.nitRate, "National income tax rate", "Broad taxes", Slider(0, 0.2, 0.005)),
    LeverSpec(L.vatRate, "Value added tax rate", "Broad taxes", Slider(0, 0.2, 0.005)),
    LeverSpec(L.salesReduction, "Sales and excise tax reduction", "Broad taxes", Slider(0, 1, 0.05),
      "0% = current; 50% ≈ eliminating sales taxes only"),
    LeverSpec(L.healthReduction, "Private health insurance replaced by public funding", "Broad taxes", Slider(0, 1, 0.05)),
    LeverSpec(L.healthHeadTax, "Employer head tax equal to employers' health contributions", "Broad taxes", Toggle),
    LeverSpec(L.wealthEvasion, "Wealth tax evasion rate", "Wealth tax", Slider(0, 1, 0.05)),
    LeverSpec(L.wealthSchedule, "Wealth tax brackets", "Wealth tax", Brackets(0.1)),
    LeverSpec(L.ftt, "0.1% financial transaction tax", "Wealth tax", Toggle,
      "Modeled as a wealth tax of 0.05% below $1m and 0.1% above"),
    LeverSpec(L.estateAvoidance, "Estate tax avoidance rate", "Estate tax", Slider(0, 1, 0.05)),
    LeverSpec(L.estateTopRate, "Estate tax top marginal rate", "Estate tax", Slider(0, 1, 0.01)),
    LeverSpec(L.corpRate, "Effective corporate tax rate on US profits", "Corporate tax", Slider(0, 1, 0.01)),
    LeverSpec(L.corpMultinationals, "Better taxation of multinationals", "Corporate tax", Toggle),
    LeverSpec(L.pitCapitalGains, "Capital gains taxed as ordinary income", "Individual income tax", Toggle),
    LeverSpec(L.pitDividends, "Dividends taxed as ordinary income", "Individual income tax", Toggle),
    LeverSpec(L.pitIntegration, "Integrate dividend and corporate taxes", "Individual income tax", Toggle),
    LeverSpec(L.pitSchedule, "Income tax brackets", "Individual income tax", Brackets(1.0)),
    LeverSpec(L.healthAsTax, "Show private health insurance as a tax", "Options", Toggle)
  ))

  /** Express a workbook-style policy as lever values (brackets at the workbook's thresholds). */
  def policyOf(p: SzPolicy, params: SzParams): Policy = Policy(
    L.nitRate -> LeverValue.Num(p.nationalIncomeTaxRate),
    L.vatRate -> LeverValue.Num(p.vatRate),
    L.salesReduction -> LeverValue.Num(p.salesTaxReduction),
    L.healthReduction -> LeverValue.Num(p.privateHealthReduction),
    L.healthHeadTax -> LeverValue.Flag(p.healthHeadTax),
    L.wealthEvasion -> LeverValue.Num(p.wealthTaxEvasion),
    L.wealthSchedule -> LeverValue.Sched(Schedule(params.wealthThresholds, p.wealthTaxRates)),
    L.ftt -> LeverValue.Flag(p.financialTransactionTax),
    L.estateAvoidance -> LeverValue.Num(p.estateTaxAvoidance),
    L.estateTopRate -> LeverValue.Num(p.estateTaxTopRate),
    L.corpRate -> LeverValue.Num(p.corporateEffectiveRate),
    L.corpMultinationals -> LeverValue.Flag(p.betterMultinationalTaxation),
    L.pitCapitalGains -> LeverValue.Flag(p.capitalGainsFullyTaxed),
    L.pitDividends -> LeverValue.Flag(p.dividendsFullyTaxed),
    L.pitIntegration -> LeverValue.Flag(p.dividendCorporateIntegration),
    L.pitSchedule -> LeverValue.Sched(Schedule(params.incomeTaxThresholds, p.incomeTaxRates))
  )

  def optionsOf(o: SzOptions): Policy = Policy(L.healthAsTax -> LeverValue.Flag(o.healthInsuranceAsTax))

  /** Group variables (names used by the instruments below). */
  def groupOf(g: SzGroup, params: SzParams): Group = Group(
    label = g.label,
    percentile = g.percentile,
    adults = g.adults,
    income = g.income,
    vars = Map(
      "baselineTotalRate" -> g.baselineTotalRate,
      "salesTaxRate" -> g.salesTaxRate,
      "propertyTaxRate" -> g.propertyTaxRate,
      "payrollTaxRate" -> g.payrollTaxRate,
      "incomeTaxRate" -> g.incomeTaxRate,
      "corporateTaxRate" -> g.corporateTaxRate,
      "estateTaxRate" -> g.estateTaxRate,
      "healthInsuranceRate" -> g.healthInsuranceRate,
      "capitalGains" -> g.capitalGains,
      "dividends" -> g.dividends,
      "fiscalIncome" -> g.fiscalIncome,
      "wealth" -> g.wealth,
      "nationalIncomeTaxBase" -> g.nationalIncomeTaxBase,
      "marginalRate" -> g.marginalRate,
      "marginalRateAbove20" -> g.marginalRateAbove20,
      "disposableCashIncome" -> g.disposableCashIncome,
      "pretaxIncome" -> g.pretaxIncome
    ),
    excess = Map(
      "wealth" -> ExcessCurve(params.wealthThresholds, pad(g.wealthExcess, params.wealthThresholds.length)),
      "taxableIncome" -> ExcessCurve(params.incomeTaxThresholds, pad(g.taxableIncomeExcess, params.incomeTaxThresholds.length))
    )
  )

  private def pad(xs: IndexedSeq[Double], n: Int) = if xs.length >= n then xs.take(n) else xs ++ IndexedSeq.fill(n - xs.length)(0.0)

  def instruments(params: SzParams): IndexedSeq[Instrument] =
    val broad = Seq("nit", "vat")
    val corporate = CorporateTax("corporate", "Corporate tax", "corporateTaxRate", L.corpRate, L.corpMultinationals,
      profits = params.corporateProfits, foreignTaxCurrent = params.currentForeignProfitsTax,
      foreignTaxReformed = params.reformedForeignProfitsTax, crowdedOutBy = broad)
    IndexedSeq(
      Fixed("property", "Property taxes", TaxCategory.Property, "propertyTaxRate"),
      Payroll("payroll", "Payroll taxes", "payrollTaxRate", Some(L.healthHeadTax), "healthInsuranceRate", params.healthHeadTaxShare),
      ScheduleTax("wealth", "Progressive wealth tax", TaxCategory.Wealth, "wealth", Some(L.wealthSchedule),
        evasionLever = Some(L.wealthEvasion)),
      ScheduleTax("ftt", "Financial transaction tax", TaxCategory.Wealth, "wealth", None,
        fixedSchedule = Some(Schedule(IndexedSeq(0.0, 1e6), IndexedSeq(params.fttWealthSurchargeFirstBracket, params.fttWealthSurcharge))),
        evasionLever = Some(L.wealthEvasion), enabledLever = Some(L.ftt)),
      FlatTax("nit", "National income tax", TaxCategory.IncomeTax, "nationalIncomeTaxBase", L.nitRate),
      ConsumptionTax("vat", "Value added tax", L.vatRate, "salesTaxRate", params.vatBaseShareOfIncome, params.vatConsumptionProfileShare),
      Reducible("health", "Private health insurance", TaxCategory.Health, "healthInsuranceRate", L.healthReduction,
        shownIf = Some(L.healthAsTax)),
      RateAndAvoidanceScaled("estate", "Estate tax", TaxCategory.Estate, "estateTaxRate", L.estateTopRate, L.estateAvoidance),
      corporate,
      UsIncomeTax("incomeTax", "Individual income tax", "incomeTaxRate", "taxableIncome", L.pitSchedule,
        L.pitCapitalGains, L.pitDividends, L.pitIntegration, corporate,
        currentFederalCorporateTax = params.currentFederalCorporateTax,
        closelyHeldRetainedEarnings = params.closelyHeldRetainedEarnings, aggregateUnit = params.aggregateUnit,
        preferentialRate = params.preferentialRate, crowdedOutBy = broad),
      Reducible("sales", "Sales and excise taxes", TaxCategory.Consumption, "salesTaxRate", L.salesReduction)
    )

  def build(
      id: String,
      name: String,
      year: Int,
      groups: IndexedSeq[SzGroup],
      params: SzParams,
      current: SzPolicy,
      options: SzOptions,
      presets: Map[String, SzPolicy],
      notes: String = ""
  ): Model =
    Model(
      id = id,
      name = name,
      year = year,
      groups = groups.map(groupOf(_, params)),
      instruments = instruments(params),
      schema = schema,
      macroData = Macro("USD", params.nationalIncome, params.aggregateUnit, params.budgetGrowth,
        params.budgetWindowOffsetYears, params.budgetWindowYears),
      currentPolicy = policyOf(current, params),
      defaultOptions = optionsOf(options),
      presets = presets.map((k, v) => k -> policyOf(v, params)),
      notes = notes
    )

  def behavioralSettings(options: SzOptions, params: SzParams): BehavioralSettings =
    taxsim.sz.SzBehavioral.settings(options, params)
