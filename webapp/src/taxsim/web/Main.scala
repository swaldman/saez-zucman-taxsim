package taxsim.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** Entry point. Routes: `#/figures` (default) and `#/simulator[?plan=…]`. */
object Main:

  private def route(hash: String): (String, String) =
    val h = hash.stripPrefix("#").stripPrefix("/")
    val (path, query) = h.indexOf('?') match
      case -1 => (h, "")
      case i  => (h.take(i), h.drop(i + 1))
    (if path == "simulator" then "simulator" else "figures", query)

  def main(args: Array[String]): Unit =
    val page = Var(route(dom.window.location.hash))
    val theme = Var(scala.util.Try(Option(dom.window.localStorage.getItem("theme")).getOrElse("auto")).getOrElse("auto"))

    def applyTheme(t: String): Unit =
      val root = dom.document.documentElement
      if t == "auto" then root.removeAttribute("data-theme") else root.setAttribute("data-theme", t)
      scala.util.Try(dom.window.localStorage.setItem("theme", t))

    def tab(key: String, label: String) =
      a(cls <-- page.signal.map(p => if p._1 == key then "tab active" else "tab"), href := s"#/$key", label)

    val app = div(
      theme.signal --> applyTheme,
      // The simulator rewrites its own URL with replaceState, which fires no hashchange; so every
      // hashchange is an external navigation (a tab click, a pasted link, back/forward): re-render.
      windowEvents(_.onHashChange) --> { _ => page.set(route(dom.window.location.hash)) },
      div(cls := "topbar",
        div(cls := "topbar-inner",
          div(cls := "brand",
            span(cls := "h1", styleAttr := "font-weight:650", "Tax Justice Simulator"),
            span(cls := "sub", "An open, live reimplementation of Saez & Zucman's taxjusticenow.org model")),
          navTag(cls := "tabs", tab("figures", "The 2020 plans"), tab("simulator", "Build your own")),
          button(cls := "theme-btn", aria.label := "Color theme",
            child.text <-- theme.signal.map {
              case "light" => "Light"
              case "dark"  => "Dark"
              case _       => "Auto"
            },
            onClick --> { _ =>
              theme.update {
                case "auto"  => "light"
                case "light" => "dark"
                case _       => "auto"
              }
            })
        )
      ),
      child <-- page.signal.map {
        case ("simulator", query) => SimulatorPage(query)
        case _                    => FiguresPage()
      },
      div(cls := "footer",
        p("Model and data: Emmanuel Saez and Gabriel Zucman, ",
          i("The Triumph of Injustice"), " (2019) and the taxjusticenow.org simulator (January 2020 version), ",
          "for 2018 US incomes and taxes. This is an independent, open-source reimplementation and is not affiliated with the authors. ",
          "Static estimates unless noted; new public spending is not modeled.")
      )
    )
    renderOnDomContentLoaded(dom.document.getElementById("app"), app)
