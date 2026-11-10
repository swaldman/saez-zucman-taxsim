package taxsim.parity

import taxsim.data.sz2019.Sz2019
import taxsim.sz.*

/** Cross-checks net revenue against the figures published by Saez & Zucman on January 20, 2020
 *  (reference/taxsimsummary_v2.pdf, Table 1 and figure notes), in % of national income,
 *  with private health insurance treated as a tax. The PDF rounds to 0.1pp. */
class PublishedRevenueTest extends munit.FunSuite:
  private val opts = Sz2019.options.copy(healthInsuranceAsTax = true)
  private def revenue(plan: String): Double =
    SzModel.run(Sz2019.groups, Sz2019.current, Sz2019.presets(plan), opts).revenueShareOfIncome

  private val published = Seq("biden" -> 0.015, "buttigieg" -> 0.039, "sanders" -> 0.029, "warren" -> 0.032, "obama" -> 0.016)

  for (plan, pub) <- published do
    test(f"$plan net revenue ≈ ${pub * 100}%.1f%% of national income (published)") {
      val r = revenue(plan)
      println(f"$plan%-10s model ${r * 100}%.3f%%  published ${pub * 100}%.1f%%")
      assertEqualsDouble(r, pub, 0.0005 + 1e-9)
    }
