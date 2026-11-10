package taxsim.parity

import taxsim.model.*
import taxsim.data.PolicyJson
import taxsim.data.sz2019.Sz2019
import taxsim.data.us.UsModel.L

/** Keeps the README example compiling and sensible. */
class ReadmeExampleTest extends munit.FunSuite:
  test("README example") {
    val us = Sz2019.model
    val warren = us.presets("warren")
    val plan = warren
      .set(L.vatRate, 0.05)
      .set(L.pitSchedule, warren.schedule(L.pitSchedule).withMinimumRateAbove(3e6, 0.6))
    val out = Engine.run(us, plan)
    val base = Engine.run(us, warren)
    println(f"Warren ${base.revenueShareOfIncome * 100}%.2f%% of NI; + 5%% VAT + 60%% above $$3m: ${out.revenueShareOfIncome * 100}%.2f%% (${out.annualRevenue}%.0f $$bn/yr)")
    assert(out.revenueShareOfIncome > base.revenueShareOfIncome)
    assertEquals(PolicyJson.read(PolicyJson.write(plan)), plan)
  }
