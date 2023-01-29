package kreuzberg.miniserver
import scalatags.Text.all.*
import scalatags.Text.tags2.noscript
import kreuzberg.scalatags._

case class Index(config: MiniServerConfig) {
  def index = html(
    head(
      script(src := config.hashedUrl("main.js")),
      config.extraJs.map { name =>
        script(src := config.hashedUrl(name))
      },
      config.extraCss.map { name =>
        link(rel := "stylesheet", href := config.hashedUrl(name))
      }
    ).addInner(config.extraHtmlHeader),
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
