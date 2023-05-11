package kreuzberg.xml

import kreuzberg.imperative.AssemblyContext
import kreuzberg.{Assembler, Html, TreeNode, HtmlEmbedding}

import scala.language.implicitConversions
import scala.xml.Elem
import kreuzberg.Component

/**
 * Implicitly converts XML to Elem. Note: Scala 3 Conversions would make us to import by hand.
 */
implicit def elemToHtml(e: Elem): Html = ScalaXmlHtml(e)

extension (tn: TreeNode) {
  def wrap: ScalaXmlTreeNodeEmbedding = ScalaXmlTreeNodeEmbedding(tn)
}

extension (component: Component) {
  def wrap(implicit c: AssemblyContext): ScalaXmlTreeNodeEmbedding = {
    c.transform(Assembler.assembleWithNewId(component)).wrap
  }
}

implicit def htmlEmbed(in: HtmlEmbedding): ScalaXmlEmbedding = in match {
  case tn: TreeNode => ScalaXmlTreeNodeEmbedding(tn)
  case h: Html      => ScalaXmlHtmlEmbedding(h)
}
