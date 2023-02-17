package kreuzberg.xml

import kreuzberg.imperative.AssemblyContext
import kreuzberg.{Assembler, Html, TreeNode}

import scala.language.implicitConversions
import scala.xml.Elem

/**
 * Implicitly converts XML to Elem. Note: Scala 3 Conversions would make us to import by hand.
 */
implicit def elemToHtml(e: Elem): Html = ScalaXmlHtml(e)

extension (tn: TreeNode) {
  def wrap: ScalaXmlHtmlEmbed = ScalaXmlHtmlEmbed(Html.treeNodeToHtml(tn))
}

extension [T](component: T)(using a: Assembler[T]) {
  def wrap(implicit c: AssemblyContext): ScalaXmlHtmlEmbed = {
    c.transform(a.assembleWithNewId(component)).wrap
  }
}

