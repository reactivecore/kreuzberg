package kreuzberg.xml

import kreuzberg.{Html, TreeNode}

import scala.language.implicitConversions
import scala.xml.Elem

/**
 * Implicitly converts XML to Elem. Note: Scala 3 Conversions would make us to import by hand.
 */
implicit def elemToHtml(e: Elem): Html = ScalaXmlHtml(e)

extension (tn: TreeNode) {
  def wrap: ScalaXmlPlaceholder = ScalaXmlPlaceholder(tn)
}