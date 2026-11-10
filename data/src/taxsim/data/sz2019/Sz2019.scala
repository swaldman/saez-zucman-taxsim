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
