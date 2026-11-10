package taxsim.sz

/** Per-group inputs of the Saez-Zucman simulator (one entry per income group, groups ranked
 *  by pre-tax income). Rates are effective rates as a share of `income`. Money amounts are
 *  per adult, in the dataset's currency. Spreadsheet column names (sheet `databig`) are in brackets.
 *
 *  @param label                  group label, e.g. "0", "99.9", "400" [gperc]
 *  @param percentile             lower bound of the group in percentiles (top-N lines use a value > 100) [gperc]
 *  @param adults                 number of adults in the group [nb]
 *  @param income                 average pre-tax income incl. pure capital gains; the denominator of all rates [denom]
 *  @param baselineTotalRate      current total effective tax rate, excl. private health insurance [effrate]
 *  @param salesTaxRate           sales and excise taxes [salestax]
 *  @param propertyTaxRate        property taxes [proptax]
 *  @param payrollTaxRate         payroll taxes / social contributions [govcontrib]
 *  @param incomeTaxRate          individual income taxes, federal + state [ditax]
 *  @param corporateTaxRate       corporate taxes, allocated to equity owners [corptax]
 *  @param estateTaxRate          estate tax [estatetax]
 *  @param healthInsuranceRate    employer-sponsored private health insurance, viewed as a poll tax [healthtax]
 *  @param capitalGains           average realized capital gains in fiscal income [fikgi]
 *  @param dividends              average dividends in fiscal income [fidiv]
 *  @param fiscalIncome           average adjusted gross income [agi]
 *  @param wealth                 average household wealth [hweal]
 *  @param wealthExcess           average wealth in excess of each wealth-tax threshold [wealth_0m … wealth_1000m]
 *  @param taxableIncomeExcess    average taxable income in excess of each income-tax threshold [taxinc_0k …]
 *  @param nationalIncomeTaxBase  base of a flat national income tax [nitaxbase]
 *  @param marginalRate           average ordinary federal marginal income tax rate [mtr]
 *  @param marginalRateAbove20    average federal marginal rate above 20% [mtrextra]
 *  @param disposableCashIncome   average disposable cash income [dicsh]
 */
final case class SzGroup(
    label: String,
    percentile: Double,
    adults: Double,
    income: Double,
    baselineTotalRate: Double,
    salesTaxRate: Double,
    propertyTaxRate: Double,
    payrollTaxRate: Double,
    incomeTaxRate: Double,
    corporateTaxRate: Double,
    estateTaxRate: Double,
    healthInsuranceRate: Double,
    capitalGains: Double,
    dividends: Double,
    fiscalIncome: Double,
    wealth: Double,
    wealthExcess: IndexedSeq[Double],
    taxableIncomeExcess: IndexedSeq[Double],
    nationalIncomeTaxBase: Double,
    marginalRate: Double,
    marginalRateAbove20: Double,
    disposableCashIncome: Double
)

/** Inputs for the optional long-run behavioral block. One entry per group, aligned with the groups.
 *  @param counterfactualIncome  pre-tax income the group would have had with "equitable" growth since a base year [growth!G]
 *  @param missingIncomeRaise    proportional income shortfall versus equitable growth [growth!L] */
final case class SzGrowth(counterfactualIncome: IndexedSeq[Double], missingIncomeRaise: IndexedSeq[Double])

/** A tax system, described with the levers of the taxjusticenow.org interface.
 *  Rates are fractions (0.37 = 37%). Bracket rate vectors are marginal rates, aligned with the
 *  thresholds in [[SzParams]]. */
final case class SzPolicy(
    nationalIncomeTaxRate: Double = 0.0,
    vatRate: Double = 0.0,
    narrowPayrollTaxRate: Double = 0.0, // present in the interface, not modeled by the workbook
    broadPayrollTaxRate: Double = 0.0,  // present in the interface, not modeled by the workbook
    agiBaseTaxRate: Double = 0.0,       // present in the interface, not modeled by the workbook
    salesTaxReduction: Double = 0.0,
    privateHealthReduction: Double = 0.0,
    healthHeadTax: Boolean = false,
    wealthTaxEvasion: Double = 0.15,
    wealthTaxRates: IndexedSeq[Double] = IndexedSeq.fill(14)(0.0),
    financialTransactionTax: Boolean = false,
    estateTaxAvoidance: Double = 0.6,
    estateTaxTopRate: Double = 0.4,
    corporateEffectiveRate: Double = 210.0 / 1308.0,
    betterMultinationalTaxation: Boolean = false,
    capitalGainsFullyTaxed: Boolean = false,
    dividendsFullyTaxed: Boolean = false,
    dividendCorporateIntegration: Boolean = false,
    incomeTaxRates: IndexedSeq[Double] = IndexedSeq(0.12, 0.22, 0.24, 0.32, 0.35, 0.37, 0.37, 0.37, 0.37, 0.37, 0.37, 0.37)
)

/** Display and behavioral options that are not tax levers. */
final case class SzOptions(
    healthInsuranceAsTax: Boolean = false,
    outputElasticity: Double = 0.25,
    bargainingElasticity: Double = 0.5
)

/** Structural constants of the model. The defaults are the values hard-coded in the 2020
 *  workbook for the US in 2018/2019 (amounts in billions of US dollars). Each field documents
 *  the spreadsheet cell it comes from. Updating the model to another year or country is largely
 *  a matter of supplying different values here plus new [[SzGroup]] data. */
final case class SzParams(
    /** Wealth-tax bracket thresholds (interface rows 22–35). */
    wealthThresholds: IndexedSeq[Double] = IndexedSeq(0, 1e6, 2.5e6, 5e6, 10e6, 25e6, 50e6, 100e6, 250e6, 500e6, 1e9, 2.5e9, 5e9, 10e9),
    /** Income-tax bracket thresholds (interface rows 51–62). */
    incomeTaxThresholds: IndexedSeq[Double] = IndexedSeq(0, 77e3, 165e3, 315e3, 400e3, 600e3, 1e6, 2.5e6, 5e6, 10e6, 25e6, 50e6),
    /** Domestic C-corporation profits, the corporate tax base (Appendix Table D2; calculatorbig Q142). */
    corporateProfits: Double = 1308.0,
    /** Current federal corporate tax on domestic profits (calculatorbig Q142: 150/1308). */
    currentFederalCorporateTax: Double = 150.0,
    /** Current tax on foreign profits of domestic multinationals (calculatorbig Q143: 8/1308). */
    currentForeignProfitsTax: Double = 8.0,
    /** Tax on foreign profits under "better taxation of multinationals" (calculatorbig R143: 96/1308). */
    reformedForeignProfitsTax: Double = 96.0,
    /** Retained earnings of closely held C-corporations (calculatorbig BB138: 166.6, from Table D2). */
    closelyHeldRetainedEarnings: Double = 166.6,
    /** Unit in which the corporate aggregates above are expressed (1e9 = billions). */
    aggregateUnit: Double = 1e9,
    /** Preferential rate cap on dividends: MIN(0.2, mtr) in calculatorbig BW. */
    preferentialRate: Double = 0.2,
    /** VAT: consumption base as a share of national income (calculatorbig S: 0.4). */
    vatBaseShareOfIncome: Double = 0.4,
    /** VAT: share allocated following the sales-tax (consumption) profile; rest is proportional to income (S: 0.75/0.25). */
    vatConsumptionProfileShare: Double = 0.75,
    /** Employer health head tax as a share of current private health contributions (Y: 0.55). */
    healthHeadTaxShare: Double = 0.55,
    /** Current top estate tax rate and avoidance rate used to scale the estate tax (U: /0.4, /(1-0.6)). */
    estateBaselineTopRate: Double = 0.4,
    estateBaselineAvoidance: Double = 0.6,
    /** Financial transaction tax, modeled as a wealth-tax surcharge in the first / other brackets (G143.., +0.0005 / +0.001). */
    fttWealthSurchargeFirstBracket: Double = 0.0005,
    fttWealthSurcharge: Double = 0.001,
    /** National income for converting revenue shares to money (interface Q8: 17546 × 1.038), in `aggregateUnit`s. */
    nationalIncome: Double = 17546.0 * 1.038,
    /** Ten-year budget window: nominal growth, first-year offset, number of years (interface R8). */
    budgetGrowth: Double = 0.04,
    budgetWindowOffsetYears: Int = 2,
    budgetWindowYears: Int = 10,
    /** Smoothing of implied marginal tax rates in the behavioral block (calculatorbig EO..FD). */
    mtrCap: Double = 0.99,
    mtrSmoothingHalfWindow: Int = 3,
    mtrSmoothingPasses: Int = 7,
    /** Bargaining response phases in linearly between these percentiles (calculatorbig DE98..DE107). */
    bargainingPhaseStart: Double = 89.0,
    bargainingPhaseEnd: Double = 99.0
)
