package taxsim.data.sz2019

import taxsim.data.{Csv, SzCodecs}
import taxsim.data.SzCodecs.given
import taxsim.sz.*

/** The US dataset of the Saez-Zucman simulator as published in January 2020
 *  (`taxsimulator2.xlsx`): 2018 incomes and taxes, 127 g-percentiles plus the top 400 adults.
 *  Source: Saez & Zucman (2019), *The Triumph of Injustice*, online appendix; data built from
 *  the US Distributional National Accounts micro-files (program usdina/programs/taxsimuweb.do). */
object Sz2019:
  val params: SzParams = SzParams()

  /** Imputation used by the workbook for the top 400's wealth above $2.5bn, $5bn and $10bn
   *  (calculator!AQ22:AS22), expressed as multiples of their wealth above $1bn. */
  val top400WealthImputation: Map[Double, Double] = Map(2.5e9 -> 0.9, 5e9 -> 0.6, 10e9 -> 0.45)

  lazy val groups: IndexedSeq[SzGroup] =
    val raw = SzCodecs.groupsFromDatabig(Csv.parse(Sz2019Raw.databigCsv), params)
    SzCodecs.imputeWealthExcess(raw, params, 1e9, top400WealthImputation, _.label == "400")

  lazy val growth: SzGrowth = SzCodecs.growthFromCsv(Csv.parse(Sz2019Raw.growthCsv))

  private lazy val presetFile = upickle.default.read[SzCodecs.PresetFile](Sz2019Raw.presetsJson)

  /** Options as saved in the workbook (health insurance not shown as a tax; elasticities 0.25 / 0.5). */
  lazy val options: SzOptions = presetFile.options

  /** Tax plans from the workbook's `interface` sheet: "current" (2018 law), "active" (the plan
   *  loaded when the workbook was saved, = Obama), "book", "biden", "buttigieg", "sanders", "warren", "obama". */
  lazy val presets: Map[String, SzPolicy] = presetFile.presets

  def current: SzPolicy = presets("current")

  /** The same data and plans as a generic [[taxsim.model.Model]] (lever-based, arbitrary brackets). */
  lazy val model: taxsim.model.Model =
    taxsim.data.us.UsModel.build("us-2018-sz2019", "United States 2018 (Saez-Zucman 2020)", 2018,
      groups, params, current, options, presets,
      notes = "Saez & Zucman, taxsimulator2.xlsx (January 2020): 2018 incomes and taxes, 127 g-percentiles + top 400.")

  /** Effective tax rates of the top 0.1% and bottom 90% by year, 1913–2018 (sheet `historical-data`,
   *  from PSZ/Saez-Zucman DINA series; % of national income + pure capital gains). */
  final case class HistoryPoint(year: Int, top01: Double, bottom90: Double, bottom90WithHealth: Double)

  lazy val history: IndexedSeq[HistoryPoint] =
    val c = Csv.parse(Sz2019Raw.historyCsv)
    val (y, t, b, bh) = (c.doubles("year"), c.doubles("top01"), c.doubles("bottom90"), c.doubles("bottom90health"))
    y.indices.map(i => HistoryPoint(y(i).toInt, t(i), b(i), bh(i)))

  /** Total effective tax rate by g-percentile (aligned with `groups`) in 1950, 1960, …, 2010, 2018
   *  (sheet `datahist`). `withHealth` includes private health insurance as a tax. */
  def historicalRates(year: Int, withHealth: Boolean): IndexedSeq[Double] =
    Csv.parse(Sz2019Raw.datahistCsv).doubles((if withHealth then "taxhealth" else "tax") + year)

  val historicalYears: IndexedSeq[Int] = IndexedSeq(1950, 1960, 1970, 1980, 1990, 2000, 2010, 2018)

  /** Proportional after-tax income shortfall versus equitable growth since 1980, by group (sheet `growth`, column M). */
  lazy val missingPosttaxRaise: IndexedSeq[Double] = Csv.parse(Sz2019Raw.growthCsv).doubles("missingPosttaxRaise")
