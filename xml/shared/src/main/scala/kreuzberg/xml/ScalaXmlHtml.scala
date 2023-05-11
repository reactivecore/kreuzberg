package kreuzberg.xml
import kreuzberg.{ComponentId, FlatHtmlBuilder, Html, TreeNode}

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

  override def embeddedNodes: Iterable[TreeNode] = {
    val collector = Vector.newBuilder[TreeNode]

    def traverse(node: Node): Unit = {
      node match {
        case h: ScalaXmlHtmlEmbedding     => collector ++= h.html.embeddedNodes
        case t: ScalaXmlTreeNodeEmbedding => collector += t.treeNode
        case other                        =>
          other.child.foreach(traverse)
      }
    }

    traverse(elem)
    collector.result()
  }

  override def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit = {
    FlatHtmlBuilder.withFlatHtmlBuilder(flatHtmlBuilder) {
      Utility.serialize(elem, sb = flatHtmlBuilder.getStringBuilder)
    }
  }
}

sealed abstract class ScalaXmlEmbedding extends SpecialNode

case class ScalaXmlHtmlEmbedding(html: Html) extends ScalaXmlEmbedding {
  override def buildString(sb: StringBuilder): StringBuilder = {
    FlatHtmlBuilder.current match {
      case Some(fb) => html.flatToBuilder(fb)
      case None     => html.render(sb)
    }
    sb
  }

  override def label: String = "#Html"
}

case class ScalaXmlTreeNodeEmbedding(treeNode: TreeNode) extends ScalaXmlEmbedding {
  override def buildString(sb: StringBuilder): StringBuilder = {
    FlatHtmlBuilder.current match {
      case Some(fb) => fb.addPlaceholder(treeNode.id)
      case None     => treeNode.renderTo(sb)
    }
    sb
  }

  override def label: String = "#TreeNode"
}
