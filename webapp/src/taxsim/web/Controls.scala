package taxsim.web

import com.raquo.laminar.api.L.*
import taxsim.model.*

/** Input controls generated from a model's [[LeverSchema]]. Elements are created once and bound
 *  to signals, so dragging a slider never re-creates the element under the pointer. */
object Controls:

  def slider(spec: LeverSpec, k: LeverKind.Slider, v: Signal[Double], current: Double, set: Double => Unit): HtmlElement =
    def show(x: Double) = if k.percent then Format.pct(x, if k.step < 0.01 then 1 else 0) else Format.withCommas(math.round(x))
    div(cls := "lever",
      div(cls := "lever-head",
        label(forId := s"lv-${spec.id}", spec.label),
        span(cls <-- v.map(x => if math.abs(x - current) > 1e-12 then "val changed" else "val"), child.text <-- v.map(show))),
      input(idAttr := s"lv-${spec.id}", typ := "range", minAttr := k.min.toString, maxAttr := k.max.toString, stepAttr := "any",
        controlled(value <-- v.map(_.toString), onInput.mapToValue.map(_.toDouble).map(x => snap(x, k)) --> set)),
      if spec.help.nonEmpty then div(cls := "help", spec.help) else emptyNode,
      div(cls := "help", child.text <-- v.map(x => if math.abs(x - current) > 1e-12 then s"Current law: ${show(current)}" else ""))
    )

  /** Snap to the slider's step grid (the input uses step=any so off-grid current values display exactly). */
  private def snap(v: Double, k: LeverKind.Slider): Double =
    val s = math.round((v - k.min) / k.step) * k.step + k.min
    math.max(k.min, math.min(k.max, math.round(s * 1e6) / 1e6))

  def toggle(spec: LeverSpec, v: Signal[Boolean], current: Boolean, set: Boolean => Unit): HtmlElement =
    div(cls := "lever",
      label(cls := "toggle",
        input(typ := "checkbox", controlled(checked <-- v, onClick.mapToChecked --> set)),
        span(spec.label, child <-- v.map(x => if x != current then span(cls := "changed-dot", title := "Changed from current law") else emptyNode))),
      if spec.help.nonEmpty then div(cls := "help", spec.help) else emptyNode
    )

  /** Editable bracket schedule: one row per bracket with a free-form threshold ("400k", "$2.5m",
   *  "1bn"), a rate slider, and add/remove buttons; plus a step-chart preview against current law.
   *  Thresholds can be anything: the engine interpolates each group's excess curve. */
  def brackets(spec: LeverSpec, k: LeverKind.Brackets, sched: Signal[Schedule], current: Schedule, set: Schedule => Unit): HtmlElement =
    var latest: Schedule = current
    val step = if k.maxRate <= 0.2 then 0.0025 else 0.005

    /** Edits act on raw (threshold, rate) pairs, which may be temporarily unsorted; normalize builds the Schedule. */
    def update(f: (IndexedSeq[Double], IndexedSeq[Double]) => (IndexedSeq[Double], IndexedSeq[Double])): Unit =
      val (ts, rs) = f(latest.thresholds, latest.rates)
      set(normalize(ts, rs))

    def row(i: Int, rowSig: Signal[(Double, Double, Int)]): HtmlElement =
      val invalid = Var(false)
      var shown = 0.0
      div(cls := "row",
        // Not `controlled`: the user types freely; the value is committed on Enter/blur (change event).
        input(typ := "text", aria.label := s"${spec.label}: threshold of bracket ${i + 1}",
          cls <-- invalid.signal.map(b => if b then "invalid" else ""),
          disabled <-- rowSig.map(_ => i == 0),
          value <-- rowSig.map { r => shown = r._1; Format.amount(r._1) },
          onInput.mapToValue --> { s => invalid.set(Format.parseAmount(s).isEmpty) },
          inContext { el =>
            onChange.mapToValue --> { s =>
              Format.parseAmount(s) match
                case Some(t) if t != shown => update((ts, rs) => (ts.updated(i, t), rs))
                case _                     => el.ref.value = Format.amount(shown) // invalid or unchanged: restore
              invalid.set(false)
            }
          }),
        input(typ := "range", minAttr := "0", maxAttr := k.maxRate.toString, stepAttr := step.toString,
          aria.label := s"${spec.label}: marginal rate of bracket ${i + 1}",
          controlled(value <-- rowSig.map(_._2.toString),
            onInput.mapToValue.map(_.toDouble) --> { r => update((ts, rs) => (ts, rs.updated(i, r))) })),
        span(cls := "rate", child.text <-- rowSig.map(r => Format.pct(r._2, if k.maxRate <= 0.2 then 2 else 1))),
        button(cls := "icon", "×", title := "Remove this bracket", aria.label := s"Remove bracket ${i + 1}",
          disabled <-- rowSig.map(r => i == 0 || r._3 <= 1),
          onClick --> { _ => update((ts, rs) => (ts.patch(i, Nil, 1), rs.patch(i, Nil, 1))) })
      )

    val rows: Signal[Seq[(Int, (Double, Double, Int))]] =
      sched.map(s => s.thresholds.indices.map(i => (i, (s.thresholds(i), s.rates(i), s.thresholds.length))))

    div(cls := "lever brackets",
      sched --> { s => latest = s },
      div(cls := "lever-head", span(spec.label),
        child <-- sched.map(s => if s != current then span(cls := "val changed", "changed") else span(cls := "val muted", ""))),
      div(cls := "row hdr", span("Above"), span("Marginal rate"), span(), span()),
      children <-- rows.split(_._1)((i, _, sig) => row(i, sig.map(_._2))),
      div(cls := "panel-actions",
        button(cls := "btn", "+ Add bracket", onClick --> { _ =>
          update { (ts, rs) =>
            val top = ts.last
            (ts :+ (if top <= 0 then 1e6 else top * 2), rs :+ rs.last)
          }
        }),
        button(cls := "btn", "Reset", disabled <-- sched.map(_ == current), onClick --> { _ => set(current) })
      ),
      preview(sched, current, k.maxRate)
    )

  /** Sorts by threshold, merges duplicate thresholds (the later row wins), keeps a bracket at 0. */
  def normalize(thresholds: IndexedSeq[Double], rates: IndexedSeq[Double]): Schedule =
    val pairs = thresholds.zip(rates).sortBy(_._1)
    val dedup = pairs.foldLeft(Vector.empty[(Double, Double)]) { (acc, p) =>
      if acc.nonEmpty && acc.last._1 == p._1 then acc.updated(acc.length - 1, p) else acc :+ p
    }
    val withZero = if dedup.headOption.exists(_._1 == 0.0) then dedup else (0.0, 0.0) +: dedup
    Schedule(withZero.map(_._1), withZero.map(_._2))

  /** Marginal-rate step chart on a log threshold axis: current law (gray) vs the edited schedule. */
  private def preview(sched: Signal[Schedule], current: Schedule, maxRate: Double): HtmlElement =
    val w = 320.0
    val h = 90.0
    div(cls := "preview",
      svg.svg(svg.viewBox := s"0 0 $w $h", svg.width := "100%",
        children <-- sched.map { s =>
          val pos = (s.thresholds ++ current.thresholds).filter(_ > 0)
          val lo = if pos.isEmpty then 1e3 else pos.min / 3
          val hi = if pos.isEmpty then 1e7 else pos.max * 3
          val ymax = math.max(1e-6, (s.rates ++ current.rates).max) * 1.15
          def px(t: Double) = 4 + (math.log(math.max(t, lo)) - math.log(lo)) / (math.log(hi) - math.log(lo)) * (w - 8)
          def py(r: Double) = h - 16 - r / ymax * (h - 24)
          def path(sc: Schedule) =
            val pts = sc.thresholds.indices.flatMap { i =>
              val x0 = px(sc.thresholds(i))
              val x1 = if i + 1 < sc.thresholds.length then px(sc.thresholds(i + 1)) else w - 4
              Seq(f"$x0%.1f,${py(sc.rates(i))}%.1f", f"$x1%.1f,${py(sc.rates(i))}%.1f")
            }
            "M" + pts.mkString(" L")
          val labels = Seq(lo * 3, math.sqrt(lo * hi), hi / 3).map(t =>
            svg.text(svg.x := px(t).toString, svg.y := (h - 3).toString, svg.textAnchor := "middle",
              svg.fill := "currentColor", svg.fontSize := "10", svg.opacity := "0.6", Format.amount(t)))
          Seq(svg.path(svg.cls := "step-cur", svg.d := path(current)), svg.path(svg.cls := "step", svg.d := path(s))) ++ labels
        }
      ),
      div(cls := "help", "Gray: current law. Blue: your schedule. Thresholds on a log scale.")
    )
