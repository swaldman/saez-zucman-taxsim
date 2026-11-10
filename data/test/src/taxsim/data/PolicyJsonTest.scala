package taxsim.data

import taxsim.data.sz2019.Sz2019

class PolicyJsonTest extends munit.FunSuite:
  test("every preset round-trips through JSON") {
    for (name, p) <- Sz2019.model.presets do assertEquals(PolicyJson.read(PolicyJson.write(p)), p, name)
  }
  test("hand-written JSON plan") {
    val p = PolicyJson.read("""{"vat.rate": 0.05, "ftt.enabled": true, "pit.schedule": {"thresholds": [0, 1e6], "rates": [0.1, 0.5]}}""")
    assertEquals(p.num("vat.rate"), 0.05)
    assert(p.flag("ftt.enabled"))
    assertEquals(p.schedule("pit.schedule").rates, IndexedSeq(0.1, 0.5))
  }
