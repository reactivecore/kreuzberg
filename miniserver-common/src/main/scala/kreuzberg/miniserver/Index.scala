package kreuzberg.miniserver
import kreuzberg.scalatags.*
import scalatags.Text.all.*
import scalatags.Text.tags2.noscript

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Index page for MiniServer. */
case class Index(config: DeploymentConfig) {
  def index(initData: Option[String]) = html(
    head(
      initData.map(encodeData),
      mainJs,
      extraJs,
      extraCss,
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

  def encodeData(initData: String) = {
    // We do not want to clash the namespace and do not want to make it trivial readable.
    // As this is on user side, the user can inspect it anyway.
    val encoded    = Base64.getEncoder.encodeToString(initData.getBytes(StandardCharsets.UTF_8))
    val scriptCode = script(
      RawFrag(
        s"""
           |window.kreuzbergInit = "${encoded}";
           |""".stripMargin
      )
    )
    scriptCode
  }

  // Caching expensive hashedUrl calls

  private val mainJs  = script(src := config.hashedUrl("main.js"))
  private val extraJs = config.extraJs.map { name =>
    script(src := config.hashedUrl(name))
  }

  private val extraCss = config.extraCss.map { name =>
    link(rel := "stylesheet", href := config.hashedUrl(name))
  }

  private val _body = body(
    div(id := "root"),
    noscript(
      config.noScriptText.getOrElse(
        "Please enable JavaScript in order to use this page."
      )
    )
  )
}
