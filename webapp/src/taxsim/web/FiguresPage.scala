package taxsim.web

import com.raquo.laminar.api.L.*
import taxsim.data.sz2019.Sz2019
import taxsim.model.*

/** The charts of the 2020 workbook, recomputed live from the model (no pasted values). */
object FiguresPage:

  def apply(): HtmlElement =
    val health = Var(true)
    val selected = Var("warren")

    val outcomes: Signal[Map[String, Outcome]] =
      health.signal.map(h => App.allPlans.map(p => p.key -> App.run(App.model.presets(p.key), h)).toMap)
    val chosen: Signal[(String, Outcome)] = outcomes.combineWith(selected.signal).map((m, k) => (k, m(k)))

    val planSeries: Signal[Seq[Series]] = outcomes.map(m =>
      App.figurePlans.map(p => Series(p.label, p.cls, m(p.key).groups.map(_.proposedTotal), emph = p.key == "current")))

    div(cls := "page",
      div(cls := "lede",
        h1("Who pays taxes in America, and who would under the 2020 plans"),
        p(cls := "secondary",
          "Total tax rates (federal, state and local; individual, corporate, payroll, consumption, property and estate taxes) ",
          "as a share of pre-tax income, for every income group from the bottom to the 400 highest-income Americans. ",
          "Every curve on this page is computed live by the open-source engine from Saez and Zucman's 2018 data and model, ",
          "reproducing their January 2020 workbook."),
        p(cls := "muted small",
          "Adults with pre-tax income above half the minimum wage (about 90% of adults), incomes of couples split equally. ",
          "Income groups are percentiles, with finer groups within the top 1%.")
      ),
      // This option scopes every chart on the page.
      div(cls := "card", styleAttr := "padding: 10px 16px",
        label(cls := "toggle",
          input(typ := "checkbox", controlled(checked <-- health.signal, onClick.mapToChecked --> health.writer)),
          span("Count employer-paid private health insurance as a tax",
            span(cls := "muted small", " (about 7% of national income; roughly a fixed amount per covered worker, so it weighs most on middle incomes)")))
      ),

      // 0. History by income group (data only: no plan, but it does depend on the health option)
      byYearCard(health.signal),

      // 1. Candidate plans
      div(cls := "card",
        div(cls := "card-head", div(cls := "titles",
          h2("Total tax rate by income group under the 2020 candidates' plans"),
          p(child.text <-- health.signal.map(h =>
            if h then "Including private health insurance as a tax (the workbook's “figurecandidates”)."
            else "Taxes only (the workbook's “figure-taxesonly”)."))
        )),
        Charts.legend(planSeries),
        Charts.lineChart(App.xs, planSeries, App.ticks, App.labels, App.groupSub, ariaLabel = "Total tax rates by income group for each plan"),
        Charts.dataTable(App.labels, planSeries),
        p(cls := "note",
          "Plans as modeled by Saez and Zucman in January 2020 (see their summary for what each plan contains and how it was approximated). ",
          "These curves are recomputed, not copied: two of the workbook's pasted curves came from an earlier data version, ",
          "which is why a few top-end points differ slightly from the published figure.")
      ),

      // 2. Revenue
      div(cls := "card",
        div(cls := "card-head", div(cls := "titles",
          h2("Revenue raised"),
          p("Net change in tax revenue relative to current law. Taxes only; new spending is not modeled.")
        )),
        div(cls := "table-wrap", styleAttr := "max-height:none",
          child <-- outcomes.combineWith(health.signal).map { (m, h) =>
            table(cls := "plans-table",
              thead(tr(th("Plan"), th("% of national income"), th("$ per year (2019)"), th("2021–2030"),
                th("Bottom 50% rate"), th("Top 0.1% rate"), th("Top 400 rate"))),
              tbody(App.allPlans.map { pl =>
                val o = m(pl.key)
                tr(
                  td(span(cls := s"key-line ${pl.cls}", styleAttr := "margin-right:8px"), pl.label),
                  td(if pl.key == "current" then "–" else Format.signedPct(o.revenueShareOfIncome)),
                  td(if pl.key == "current" then "–" else Format.bn(o.annualRevenue, signed = true)),
                  td(if pl.key == "current" then "–" else Format.bn(o.budgetWindowRevenue, signed = true)),
                  td(Format.pct(App.groupRate(o, true, App.bottom50))),
                  td(Format.pct(App.groupRate(o, true, App.top01))),
                  td(Format.pct(App.groupRate(o, true, App.top400)))
                )
              })
            )
          }
        ),
        p(cls := "note", child.text <-- health.signal.map(h =>
          if h then "With private health insurance counted as a tax, the Warren and Sanders figures are net of replacing it with public funding (published: Biden 1.5%, Buttigieg 3.9%, Sanders 2.9%, Warren 3.2%)."
          else "With private health insurance not counted, the Warren and Sanders figures include the new taxes that replace private premiums (~7% of national income)."))
      ),

      // The charts below show one plan at a time; this selector scopes them.
      div(cls := "card", styleAttr := "padding: 10px 16px",
        div(cls := "share-row", styleAttr := "flex-wrap: wrap; gap: 10px",
          label(forId := "plan-select", cls := "secondary", "Plan shown in the charts below:"),
          select(idAttr := "plan-select",
            controlled(value <-- selected.signal, onChange.mapToValue --> selected.writer),
            App.allPlans.filterNot(_.key == "current").map(p => option(value := p.key, p.label))
          )
        )
      ),

      // 3. Breakdown
      {
        val cur = outcomes.map(m => App.breakdown(m("current"), proposed = false))
        val prop = chosen.map((_, o) => App.breakdown(o, proposed = true))
        // A shared y-scale so the two stacks can be compared directly.
        val shared = cur.combineWith(prop).map { (a, b) =>
          def top(ss: Seq[Series]) = App.xs.indices.map(i => ss.map(_.values(i)).sum).max
          Some((0.0, math.max(top(a), top(b))))
        }
        div(cls := "grid-2",
          breakdownCard("Current tax rates, by tax", Val(span("Current law, 2018")), cur, shared),
          breakdownCard("Proposed tax rates, by tax", chosen.map((k, _) => planSub("Under ", k, "")), prop, shared))
      },

      // 4. History
      historyCard(health.signal, chosen),

      // 5. Long-run effects
      behavioralCards(chosen)
    )

  /** A subtitle with the (changing) plan name emphasized. */
  private def planSub(before: String, key: String, after: String): HtmlElement =
    span(before, span(cls := "plan-name", App.planLabel(key)), after)

  private def breakdownCard(title: String, sub: Signal[HtmlElement], series: Signal[Seq[Series]],
      yr: Signal[Option[(Double, Double)]]): HtmlElement =
    div(cls := "card",
      div(cls := "card-head", div(cls := "titles", h2(title), p(child <-- sub))),
      Charts.legend(series, rects = true),
      Charts.lineChart(App.xs, series, App.ticks, App.labels, App.groupSub, stacked = true, height = 300, yRangeSig = yr, ariaLabel = title),
      Charts.dataTable(App.labels, series.map(App.withTotal))
    )

  private def historyCard(health: Signal[Boolean], chosen: Signal[(String, Outcome)]): HtmlElement =
    val years = Sz2019.history.map(_.year.toDouble)
    val series = health.map { h =>
      Seq(
        Series("Top 0.1%", "series-2", Sz2019.history.map(_.top01)),
        Series("Bottom 90%", "series-1", Sz2019.history.map(p => if h then p.bottom90WithHealth else p.bottom90))
      )
    }
    val markerYear = 2025.0
    // Each plan dot is joined by a dotted line to the series' latest historical value.
    val markers = chosen.combineWith(series).map { (_, o, ss) =>
      def latest(s: Series) = s.values.lastIndexWhere(!_.isNaN) match
        case -1 => None
        case i  => Some((years(i), s.values(i)))
      Seq(
        Marker(markerYear, App.groupRate(o, true, App.top01), "series-2", "Top 0.1%", latest(ss(0))),
        Marker(markerYear, App.groupRate(o, true, App.bottom90), "series-1", "Bottom 90%", latest(ss(1)))
      )
    }
    val xs = years :+ markerYear
    val padded = series.map(_.map(s => s.copy(values = s.values :+ Double.NaN)))
    val ticks = (1920 to 2020 by 10).map(y => y.toDouble -> y.toString) :+ (markerYear -> "Plan")
    div(cls := "card",
      div(cls := "card-head", div(cls := "titles",
        h2("The proposed plan in historical context"),
        p(child <-- chosen.map((k, _) => planSub("Tax rates of the top 0.1% and the bottom 90%, 1913–2018, and under ", k, " (dots at right edge)."))))),
      Charts.legend(padded),
      Charts.lineChart(xs, padded, ticks, i => if i < years.length then years(i).toInt.toString else "Proposed plan",
        markers = markers, height = 300, ariaLabel = "Historical tax rates of the top 0.1% and bottom 90%"),
      Charts.dataTable(xs.map(x => if x == markerYear then "Plan" else x.toInt.toString).toIndexedSeq,
        padded.combineWith(markers).map((ss, mk) => ss.zip(mk).map((s, m) => s.copy(values = s.values.updated(s.values.length - 1, m.y))))),
      p(cls := "note", "Historical series: Piketty, Saez and Zucman distributional national accounts, as in the workbook's “historical-data” sheet. Rates as a share of national income plus pure capital gains.")
    )

  private def byYearCard(health: Signal[Boolean]): HtmlElement =
    val years = Seq(1950 -> "ramp-1", 1980 -> "ramp-2", 2000 -> "ramp-3", 2018 -> "ramp-4")
    val series = health.map(h => years.map((y, c) => Series(y.toString, c, Sz2019.historicalRates(y, h))))
    div(cls := "card",
      div(cls := "card-head", div(cls := "titles",
        h2("Tax rates by income group over time"),
        p("Total tax rate by income group in 1950, 1980, 2000 and 2018 (the workbook's historical animation shows every decade). ",
          child.text <-- health.map(h =>
            if h then "Private health insurance counted as a tax." else "Private health insurance not counted as a tax.")))),
      Charts.legend(series),
      Charts.lineChart(App.xs, series, App.ticks, App.labels, App.groupSub, ariaLabel = "Tax rates by income group in selected years"),
      Charts.dataTable(App.labels, series)
    )

  private def behavioralCards(chosen: Signal[(String, Outcome)]): HtmlElement =
    val b = chosen.map((k, o) => (k, App.behavioral(o)))
    val mtr = b.map((_, r) => Seq(
      Series("Current law", "series-1", r.groups.map(_.currentMtr)),
      Series("Proposed", "series-2", r.groups.map(_.proposedMtr))))

    /** Income changes under the plan, relative to current income (unrounded). */
    final case class Changes(pretax: IndexedSeq[Double], aftertaxLongRun: IndexedSeq[Double], aftertaxStatic: IndexedSeq[Double])
    val changes = chosen.combineWith(b).map { (_, o, _, r) =>
      val y = o.groups.map(_.group.income)
      val e = o.groups.map(_.baselineTotal)
      val z = o.groups.map(_.proposedTotal)
      val di = r.groups.map(_.dynamicIncome)
      Changes(
        pretax = y.indices.map(i => di(i) / y(i) - 1),
        aftertaxLongRun = y.indices.map(i => di(i) * (1 - z(i)) / (y(i) * (1 - e(i))) - 1),
        aftertaxStatic = y.indices.map(i => (1 - z(i)) / (1 - e(i)) - 1))
    }
    /** The raise still needed to reach the equitable path, relative to income under the plan. */
    def stillNeeded(needed: IndexedSeq[Double], change: IndexedSeq[Double]) =
      needed.indices.map(i => (1 + needed(i)) / (1 + change(i)) - 1)

    val neededPre = b.map((_, r) => r.groups.map(_.missingIncomeRaise))
    val neededPost = Sz2019.missingPosttaxRaise
    val pretaxGrey = "Raise needed to match average income growth since 1980"
    val aftertaxGrey = "Raise needed to match average after-tax income growth since 1980"
    // Legend convention: the current situation first, the plan second (in orange).
    val pretax = neededPre.combineWith(changes).map((n, c) => Seq(
      Series(pretaxGrey, "muted-series", n),
      Series("Raise still needed with the plan (long-run, including behavioral responses)", "series-2", stillNeeded(n, c.pretax))))
    val pretaxTable = pretax.combineWith(changes).map((ss, c) =>
      ss :+ Series("Plan's change in pre-tax income", "", c.pretax))
    val aftertax = changes.map(c => Seq(
      Series(aftertaxGrey, "muted-series", neededPost),
      Series("Raise still needed with the plan (long-run, including behavioral responses)", "series-2", stillNeeded(neededPost, c.aftertaxLongRun)),
      Series("Raise still needed with tax changes alone (static)", "series-2 dashed", stillNeeded(neededPost, c.aftertaxStatic))))
    val aftertaxTable = aftertax.combineWith(changes).map((ss, c) => ss ++ Seq(
      Series("Plan's change in after-tax income (long-run)", "", c.aftertaxLongRun),
      Series("Plan's change in after-tax income (static)", "", c.aftertaxStatic)))

    // Where a plan cuts a group's income to a small fraction, the raise still needed explodes; the axis is capped.
    val cap = (-1.0, 3.0)
    def card(t: String, sub: Signal[HtmlElement], s: Signal[Seq[Series]], table: Option[Signal[Seq[Series]]] = None,
        capped: Boolean = false) =
      div(cls := "card",
        div(cls := "card-head", div(cls := "titles", h2(t), p(child <-- sub))),
        Charts.legend(s),
        Charts.lineChart(App.xs, s, App.ticks, App.labels, App.groupSub, height = 280, ariaLabel = t,
          yClip = if capped then Some(cap) else None),
        Charts.dataTable(App.labels, table.getOrElse(s)),
        if !capped then emptyNode
        else child.maybe <-- s.map { ss =>
          val beyond = ss.flatMap(_.values).filterNot(_.isNaN).filter(v => v > cap._2 || v < cap._1)
          Option.when(beyond.nonEmpty)(p(cls := "note",
            s"Some values are off the chart (up to ${Format.pct(beyond.max, 0)}): the plan cuts those groups' income so far below the ",
            "equitable path that the raise needed to get back to it is very large. Hover over the chart or open the data table to see them."))
        })
    div(
      div(cls := "card", styleAttr := "margin-top:16px",
        h2("Long-run effects (experimental)"),
        p(cls := "secondary small",
          "Saez and Zucman's optional behavioral block: marginal tax rates implied by the rate schedule across groups, a real (output) response ",
          "with elasticity 0.25, and a “bargaining” response at the top (elasticity 0.5, phased in from P90 to P99) whose income is redistributed ",
          "to groups in proportion to how far their income has fallen behind equitable growth since 1980."),
        p(cls := "secondary small",
          "“Equitable growth” means every income group's income growing at the same rate as average income between 1980 and 2018 ",
          "(average pre-tax income per adult grew about 4.8-fold in nominal terms). The grey lines show the raise each group would need ",
          "today to be on that path: above 100% means its income would have to more than double; below zero means the group has ",
          "grown faster than average. The orange lines show the raise each group would still need after the plan, relative to its ",
          "income under the plan; the closer to zero, the closer the plan brings the group to the equitable path. The path itself is ",
          "held at its 2018 level. Groups are percentile positions, not the same people in both years. The data tables also give ",
          "each plan's own percentage change in income."),
        p(cls := "secondary small",
          "The model counts taxes only: apart from replacing private health premiums, it does not include how the new revenue is spent."),
        p(cls := "secondary small",
          "The pre-tax gains of lower groups come mostly from the bargaining assumption: income the top no longer bargains for is assumed ",
          "to go to groups below the equitable path, in proportion to their dollar shortfall, so each such group's raise is the same ",
          "fraction of the raise it needed.")),
      div(cls := "grid-2", styleAttr := "margin-top:16px",
        card("Implied marginal tax rates", b.map((k, _) => planSub("Smoothed, current law vs ", k, "")), mtr),
        card("Pre-tax income", b.map((k, _) => planSub("Distance from the equitable path, today and under ", k, " (long-run)")), pretax, Some(pretaxTable), capped = true)
      ),
      div(styleAttr := "margin-top:16px",
        card("After-tax income", b.map((k, _) => planSub("Distance from the equitable path, today and under ", k, "")), aftertax, Some(aftertaxTable), capped = true)
      )
    )
