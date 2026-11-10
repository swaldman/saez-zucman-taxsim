package taxsim.data

import taxsim.sz.*
import upickle.default.*

/** JSON codecs for Saez-Zucman model types (kept out of `core` so the engine stays dependency-free). */
object SzCodecs:
  given ReadWriter[SzPolicy] = macroRW
  given ReadWriter[SzOptions] = macroRW
  given ReadWriter[SzParams] = macroRW

  /** A named set of tax plans plus model options, as stored in `presets.json`. */
  final case class PresetFile(options: SzOptions, presets: Map[String, SzPolicy])
  given ReadWriter[PresetFile] = macroRW

  /** Build groups from a table with the column names of the workbook's `databig` sheet. */
  def groupsFromDatabig(csv: Csv, params: SzParams = SzParams()): IndexedSeq[SzGroup] =
    def wealthCol(t: Double): String =
      val m = t / 1e6
      if m == math.rint(m) then s"wealth_${m.toLong}m" else s"wealth_${m.toString.replace('.', 'p')}m"
    def incomeCol(t: Double): String = s"taxinc_${(t / 1e3).toLong}k"
    val wealthCols = params.wealthThresholds.map(t => csv.doublesOrZero(wealthCol(t)))
    val incomeCols = params.incomeTaxThresholds.map(t => csv.doubles(incomeCol(t)))
    val labels = csv.column("gperc")
    val pct = csv.doubles("gperc")
    val c = (name: String) => csv.doubles(name)
    val (nb, denom, effrate, sales, prop, payroll, ditax, corp, estate, health) =
      (c("nb"), c("denom"), c("effrate"), c("salestax"), c("proptax"), c("govcontrib"), c("ditax"), c("corptax"), c("estatetax"), c("healthtax"))
    val (kg, div, agi, hweal, nibase, mtr, mtrx, dicsh) =
      (c("fikgi"), c("fidiv"), c("agi"), c("hweal"), c("nitaxbase"), c("mtr"), c("mtrextra"), c("dicsh"))
    IndexedSeq.tabulate(csv.rows.length) { i =>
      SzGroup(
        label = labels(i).trim,
        percentile = pct(i),
        adults = nb(i),
        income = denom(i),
        baselineTotalRate = effrate(i),
        salesTaxRate = sales(i),
        propertyTaxRate = prop(i),
        payrollTaxRate = payroll(i),
        incomeTaxRate = ditax(i),
        corporateTaxRate = corp(i),
        estateTaxRate = estate(i),
        healthInsuranceRate = health(i),
        capitalGains = kg(i),
        dividends = div(i),
        fiscalIncome = agi(i),
        wealth = hweal(i),
        wealthExcess = wealthCols.map(_(i)),
        taxableIncomeExcess = incomeCols.map(_(i)),
        nationalIncomeTaxBase = nibase(i),
        marginalRate = mtr(i),
        marginalRateAbove20 = mtrx(i),
        disposableCashIncome = dicsh(i)
      )
    }

  /** Impute missing wealth exceedances above the highest observed threshold for the groups selected by
   *  `which`, as fixed multiples of the exceedance at `fromThreshold`. The 2020 workbook does this for
   *  the top 400 only (calculator!AQ22:AS22): wealth above $2.5bn, $5bn, $10bn = 0.9, 0.6, 0.45 × wealth above $1bn. */
  def imputeWealthExcess(
      groups: IndexedSeq[SzGroup],
      params: SzParams,
      fromThreshold: Double,
      factors: Map[Double, Double],
      which: SzGroup => Boolean
  ): IndexedSeq[SzGroup] =
    val from = params.wealthThresholds.indexOf(fromThreshold)
    require(from >= 0, s"threshold $fromThreshold not among wealth thresholds")
    groups.map { g =>
      if !which(g) then g
      else
        val base = g.wealthExcess(from)
        val ex = params.wealthThresholds.indices.map { k =>
          factors.get(params.wealthThresholds(k)) match
            case Some(f) => f * base
            case None    => if k < g.wealthExcess.length then g.wealthExcess(k) else 0.0
        }
        g.copy(wealthExcess = ex)
    }

  def growthFromCsv(csv: Csv): SzGrowth =
    SzGrowth(csv.doubles("peinc2018"), csv.doubles("missingPretaxRaise"))
