package kreuzberg.miniserver
import kreuzberg.scalatags.*
import scalatags.Text.all.*
import scalatags.Text.tags2.noscript

/** Index page for MiniServer. */
case class Index(config: MiniServerConfig[_]) {
  def index = html(
    head(
      script(src := config.hashedUrl("main.js")),
      config.extraJs.map { name =>
        script(src := config.hashedUrl(name))
      },
      config.extraCss.map { name =>
        link(rel := "stylesheet", href := config.hashedUrl(name))
      },
      config.extraHtmlHeader
    ),
    body(
      div(id := "root"),
      noscript(
        config.noScriptText.getOrElse(
          "Please enable JavaScript in order to use this page."
        )
      )
    )
  )
}
