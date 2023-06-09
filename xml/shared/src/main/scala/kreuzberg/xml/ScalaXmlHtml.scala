package kreuzberg.xml
import kreuzberg.xml.ScalaXmlHtml.maybeEmbed
import kreuzberg.{Component, FlatHtmlBuilder, Html, Identifier}

import scala.xml.{Elem, Node, Null, SpecialNode, UnprefixedAttribute, Utility}

case class ScalaXmlHtml(elem: Elem) extends Html {
  override def withId(id: Identifier): Html = {
    val attribute = new UnprefixedAttribute(
      "data-id",
      id.value.toString,
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

  override def embeddedComponents: Iterable[Component] = {
    val collector = Vector.newBuilder[Component]

    def traverse(node: Node): Unit = {
      node match {
        case h: ScalaXmlHtmlEmbedding      => collector ++= h.html.embeddedComponents
        case t: ScalaXmlComponentEmbedding => collector += t.component
        case other                         =>
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

  override def appendChild(html: Html): Html = {
    ScalaXmlHtml(
      elem.copy(
        child = elem.child :+ maybeEmbed(html)
      )
    )
  }

  override def prependChild(html: Html): Html = {
    ScalaXmlHtml(
      elem.copy(
        child = maybeEmbed(html) +: elem.child
      )
    )
  }
}

object ScalaXmlHtml {
  def maybeEmbed(html: Html): Node = {
    html match {
      case s: ScalaXmlHtml => s.elem
      case other           => ScalaXmlHtmlEmbedding(html)
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

case class ScalaXmlComponentEmbedding(component: Component) extends ScalaXmlEmbedding {
  override def buildString(sb: StringBuilder): StringBuilder = {
    FlatHtmlBuilder.current match {
      case Some(fb) => fb.addPlaceholder(component.id)
      case None     => sb ++= s"<component id=\"${component.id}\">"
    }
    sb
  }

  override def label: String = "#Component"
}
