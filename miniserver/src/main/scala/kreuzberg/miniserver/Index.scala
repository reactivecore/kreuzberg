package kreuzberg.miniserver
import scalatags.Text.all.*
import scalatags.Text.tags2.noscript

case class Index(config: MiniServerConfig) {
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
        "Please enable JavaScript in order to use this page."
      )
    )
  )
}
