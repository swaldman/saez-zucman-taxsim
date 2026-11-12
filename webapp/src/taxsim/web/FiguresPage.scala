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
      Charts.dataTable(App.labels, series)
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
    // Legend convention: the current situation first, the proposed change second (in orange).
    val pretax = b.map((_, r) => Seq(
      Series("Shortfall vs equitable growth since 1980", "muted-series", r.groups.map(_.missingIncomeRaise)),
      Series("Long-run change with the plan", "series-2", r.groups.map(_.pretaxIncomeGain))))
    val aftertax = b.map((_, r) => Seq(
      Series("Shortfall vs equitable growth since 1980", "muted-series", Sz2019.missingPosttaxRaise),
      Series("Change with the plan (static)", "series-2", r.groups.map(_.aftertaxIncomeGainStatic))))
    def card(t: String, sub: Signal[HtmlElement], s: Signal[Seq[Series]], yr: Option[(Double, Double)] = None) =
      div(cls := "card",
        div(cls := "card-head", div(cls := "titles", h2(t), p(child <-- sub))),
        Charts.legend(s),
        Charts.lineChart(App.xs, s, App.ticks, App.labels, App.groupSub, yRange = yr, height = 280, ariaLabel = t),
        Charts.dataTable(App.labels, s))
    div(
      div(cls := "card", styleAttr := "margin-top:16px",
        h2("Long-run effects (experimental)"),
        p(cls := "secondary small",
          "Saez and Zucman's optional behavioral block: marginal tax rates implied by the rate schedule across groups, a real (output) response ",
          "with elasticity 0.25, and a “bargaining” response at the top (elasticity 0.5, phased in from P90 to P99) whose income is redistributed ",
          "to groups in proportion to how far their income has fallen behind equitable growth since 1980.")),
      div(cls := "grid-2", styleAttr := "margin-top:16px",
        card("Implied marginal tax rates", b.map((k, _) => planSub("Smoothed, current law vs ", k, "")), mtr),
        card("Pre-tax income", b.map((k, _) => planSub("Long-run % change under ", k, "")), pretax)
      ),
      div(styleAttr := "margin-top:16px",
        card("After-tax income", b.map((k, _) => planSub("% change under ", k, ", before behavioral responses")), aftertax)
      )
    )
