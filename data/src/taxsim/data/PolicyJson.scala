package taxsim.data

import taxsim.model.{LeverValue, Policy, Schedule}

/** Compact JSON for policies, suitable for files and URLs:
 *  {{{
 *  { "vat.rate": 0.05, "ftt.enabled": true,
 *    "pit.schedule": { "thresholds": [0, 77000, 400000], "rates": [0.12, 0.22, 0.45] } }
 *  }}}
 *  Numbers are [[LeverValue.Num]], booleans [[LeverValue.Flag]], objects [[LeverValue.Sched]]. */
object PolicyJson:

  def toJson(p: Policy): ujson.Value =
    ujson.Obj.from(p.values.toSeq.sortBy(_._1).map((k, v) => k -> valueToJson(v)))

  def write(p: Policy, indent: Int = -1): String = ujson.write(toJson(p), indent)

  def fromJson(js: ujson.Value): Policy =
    Policy(js.obj.iterator.map((k, v) => k -> valueFromJson(k, v)).toMap)

  def read(s: String): Policy = fromJson(ujson.read(s))

  private def valueToJson(v: LeverValue): ujson.Value = v match
    case LeverValue.Num(x)   => ujson.Num(x)
    case LeverValue.Flag(b)  => ujson.Bool(b)
    case LeverValue.Sched(s) => ujson.Obj("thresholds" -> ujson.Arr.from(s.thresholds.map(ujson.Num(_))), "rates" -> ujson.Arr.from(s.rates.map(ujson.Num(_))))

  private def valueFromJson(key: String, v: ujson.Value): LeverValue = v match
    case ujson.Num(x)  => LeverValue.Num(x)
    case b: ujson.Bool => LeverValue.Flag(b.value)
    case o: ujson.Obj  => LeverValue.Sched(Schedule(o("thresholds").arr.map(_.num).toIndexedSeq, o("rates").arr.map(_.num).toIndexedSeq))
    case other         => throw new IllegalArgumentException(s"lever '$key': unsupported JSON value $other")
