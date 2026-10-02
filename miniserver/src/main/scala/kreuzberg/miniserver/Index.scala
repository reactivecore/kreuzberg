package kreuzberg.miniserver

import kreuzberg.scalatags.*
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.Text.tags2.noscript

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Index page for MiniServer. */
case class Index(config: DeploymentConfig) {

  /** Rendered index page. */
  def index(initData: Option[String], translations: Option[PageTranslations] = None): TypedTag[String] = {
    // With translations, lang must not be part of htmlRootAttributes:
    // ScalaTags joins both values into one invalid attribute (the MiniServer logs an error).
    html(translations.map(t => lang := t.locale), config.htmlRootAttributes)(
      head(
        config.extraHtmlHeader,
        initData.map(encodeData),
        translations.map(t => script(RawFrag(t.scriptCode))),
        mainJs,
        extraJs,
        extraCss
      ),
      body(
        div(id := "root"),
        noscript(
          if (config.noScriptBody.nonEmpty) config.noScriptBody
          else
            config.noScriptText.getOrElse(
              "Please enable JavaScript in order to use this page."
            )
        )
      )
    )
  }

  def pageHtml(initData: Option[String], translations: Option[PageTranslations] = None): String = {
    "<!DOCTYPE html>\n" + index(initData, translations).toString
  }

  private def encodeData(initData: String): TypedTag[String] = {
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
}

object Index {

  /** Returns true if the modifiers set a `lang` attribute. */
  def hasLangAttribute(modifiers: Seq[Modifier]): Boolean = {
    modifiers.exists {
      case pair: scalatags.generic.AttrPair[?, ?] => pair.a.name == "lang"
      case _                                      => false
    }
  }
}
