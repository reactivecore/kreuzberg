package kreuzberg.xml

import kreuzberg.{Assembler, Html, TreeNode, HtmlEmbedding}

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

implicit def htmlEmbed(in: HtmlEmbedding): ScalaXmlEmbedding = in match {
  case c: Component => ScalaXmlComponentEmbedding(c)
  case h: Html      => ScalaXmlHtmlEmbedding(h)
}
