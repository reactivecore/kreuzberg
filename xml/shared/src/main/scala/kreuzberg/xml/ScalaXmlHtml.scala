package kreuzberg.xml
import kreuzberg.imperative.PlaceholderState
import kreuzberg.{ComponentId, Html, TreeNode}

import scala.xml.{Elem, Node, Null, SpecialNode, UnprefixedAttribute, Utility}

case class ScalaXmlHtml(elem: Elem) extends Html {
  override def withId(id: ComponentId): Html = {
    val attribute = new UnprefixedAttribute(
      "data-id",
      id.toString,
      Null
    )

    ScalaXmlHtml(
      elem.copy(
        attributes = elem.attributes.append(attribute)
      )
    )
  }

  def addComment(c: String): Html = {
    ScalaXmlHtml(
      elem.copy(
        // Note this may change the order, but comments should be unrelated
        child = scala.xml.Comment(" " + c.replace("--", "").trim() + " ") +: elem.child
      )
    )
  }

  override def addInner(inner: Seq[Html]): Html = {
    val converted = inner.map {
      case s: ScalaXmlHtml => s.elem
      case other           => ScalaXmlHtmlEmbed(other)
    }
    ScalaXmlHtml(
      elem.copy(
        child = elem.child ++ converted
      )
    )
  }

  override def placeholders: Iterable[TreeNode] = {
    val collector = Vector.newBuilder[TreeNode]

    def traverse(node: Node): Unit = {
      node match {
        case h: ScalaXmlHtmlEmbed => collector ++= h.html.placeholders
        case other                =>
          other.child.foreach(traverse)
      }
    }

    traverse(elem)
    collector.result()
  }

  override def render(sb: StringBuilder): Unit = {
    Utility.serialize(elem, sb = sb)
  }
}

case class ScalaXmlHtmlEmbed(html: Html) extends SpecialNode {
  override def buildString(sb: StringBuilder): StringBuilder = {
    html.render(sb)
    sb
  }

  override def label: String = "#Html"
}
