package kreuzberg.xml

import kreuzberg.{Html, HtmlEmbedding}

import scala.language.implicitConversions
import scala.xml.Elem
import kreuzberg.Component

/**
 * Implicitly converts XML to Elem. Note: Scala 3 Conversions would make us to import by hand.
 */
implicit def elemToHtml(e: Elem): Html = ScalaXmlHtml(e)

extension (component: Component) {
  def wrap: ScalaXmlComponentEmbedding = {
    ScalaXmlComponentEmbedding(component)
  }
}

extension (html: Html) {

  /** Embeds HTML of another flavour (e.g. from `trHtml`) into XML; without, `{html}` would be inserted as text. */
  def wrap: ScalaXmlHtmlEmbedding = {
    ScalaXmlHtmlEmbedding(html)
  }
}

implicit def htmlEmbed(in: HtmlEmbedding): ScalaXmlEmbedding = in match {
  case c: Component => ScalaXmlComponentEmbedding(c)
  case h: Html      => ScalaXmlHtmlEmbedding(h)
}
