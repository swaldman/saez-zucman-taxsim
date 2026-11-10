package taxsim.web

import taxsim.model.Group

object Format:

  private def trimZeros(s: String): String =
    if s.contains('.') then s.reverse.dropWhile(_ == '0').dropWhile(_ == '.').reverse else s

  private def fixed(x: Double, digits: Int): String =
    val f = math.pow(10, digits)
    val r = math.round(x * f) / f
    val neg = r < 0
    val a = math.abs(r)
    val whole = math.floor(a).toLong
    val frac = math.round((a - whole) * f)
    val s =
      if digits == 0 then whole.toString
      else
        val fs = frac.toString
        whole.toString + "." + ("0" * (digits - fs.length)) + fs
    (if neg && r != 0 then "−" else "") + s

  /** 0.2734 → "27.3%". */
  def pct(x: Double, digits: Int = 1): String = fixed(x * 100, digits) + "%"

  /** Signed percentage points: 0.0156 → "+1.56%". */
  def signedPct(x: Double, digits: Int = 2): String = (if x > 0 then "+" else "") + pct(x, digits)

  def withCommas(n: Long): String =
    val s = math.abs(n).toString
    val g = s.reverse.grouped(3).mkString(",").reverse
    if n < 0 then "−" + g else g

  /** Money in billions: 284.4 → "$284bn"; 3692 → "$3.69tn". */
  def bn(x: Double, signed: Boolean = false): String =
    val sign = if x < 0 then "−" else if signed && x > 0 then "+" else ""
    val a = math.abs(x)
    val body = if a >= 1000 then "$" + fixed(a / 1000, 2) + "tn" else "$" + withCommas(math.round(a)) + "bn"
    sign + body

  /** A dollar amount in compact form: 77000 → "$77k", 2.5e6 → "$2.5m", 1e9 → "$1bn". */
  def amount(x: Double): String =
    val a = math.abs(x)
    val s =
      if a >= 1e9 then trimZeros(fixed(a / 1e9, 2)) + "bn"
      else if a >= 1e6 then trimZeros(fixed(a / 1e6, 2)) + "m"
      else if a >= 1e3 then trimZeros(fixed(a / 1e3, 1)) + "k"
      else withCommas(math.round(a))
    (if x < 0 then "−$" else "$") + s

  /** Parses "77k", "$2.5m", "1bn", "1,000,000", "3e6". */
  def parseAmount(s: String): Option[Double] =
    val t = s.trim.toLowerCase.replace("$", "").replace(",", "").replace(" ", "")
    val (num, mult) =
      if t.endsWith("bn") then (t.dropRight(2), 1e9)
      else if t.endsWith("b") then (t.dropRight(1), 1e9)
      else if t.endsWith("m") then (t.dropRight(1), 1e6)
      else if t.endsWith("k") then (t.dropRight(1), 1e3)
      else (t, 1.0)
    num.toDoubleOption.filter(v => v >= 0 && !v.isNaN && !v.isInfinite).map(_ * mult)

  private def pctNum(p: Double): String = trimZeros(fixed(p, 3))

  /** "P99.9–99.91", "Top 400", "P0–1". */
  def groupLabel(groups: IndexedSeq[Group], i: Int): String =
    val g = groups(i)
    if g.percentile > 100 then s"Top ${g.label}"
    else
      val next = groups.lift(i + 1)
      next match
        case Some(n) if n.percentile <= 100 => s"P${pctNum(g.percentile)}–${pctNum(n.percentile)}"
        case Some(n)                         => s"P${pctNum(g.percentile)}–top ${n.label}"
        case None                            => s"P${pctNum(g.percentile)}–100"

  /** X-axis ticks for g-percentile charts: deciles, then Top 1%, 0.1%, 0.01% and the top-N line. */
  def groupTicks(groups: IndexedSeq[Group]): Seq[(Double, String)] =
    val dec = (0 to 90 by 10).flatMap(p => groups.indexWhere(_.percentile >= p) match
      case -1 => None
      case i  => Some(i.toDouble -> s"$p%"))
    val tops = Seq(99.0 -> "Top 1%", 99.9 -> "0.1%", 99.99 -> "0.01%").flatMap((p, l) =>
      groups.indexWhere(g => g.percentile >= p - 1e-9) match
        case -1 => None
        case i  => Some(i.toDouble -> l))
    val topN = groups.indexWhere(_.percentile > 100) match
      case -1 => Nil
      case i  => Seq(i.toDouble -> s"Top ${groups(i).label}")
    dec ++ tops ++ topN
