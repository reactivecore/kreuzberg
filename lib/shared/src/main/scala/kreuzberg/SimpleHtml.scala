package kreuzberg

import kreuzberg.SimpleHtmlNode.Wrapper

import scala.annotation.static
import scala.collection.immutable.Vector

/** Trivial implementation of Html. Note: just implemented enough to get it barely working. */
case class SimpleHtml(
    tag: String,
    attributes: Vector[(String, Option[String])] = Vector.empty,
    children: Vector[SimpleHtmlNode] = Vector.empty,
    comment: String = ""
) extends Html
    with SimpleHtmlNode {
  override def withId(id: Identifier): Html = {
    withAttribute("data-id", Some(id.toString))
  }

  def addComment(c: String): Html = {
    copy(comment = comment + c)
  }

  def withAttribute(name: String, value: Option[String]): Html = {
    copy(
      attributes = (attributes.view.filterNot(_._1 == name) ++ Seq(name -> value)).toVector
    )
  }

  override def embeddedComponents: Iterable[Component] = {
    children.flatMap(_.embeddedComponents)
  }

  override def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit = {
    val escaped = SimpleHtmlNode.escape(tag)
    flatHtmlBuilder ++= "<"
    flatHtmlBuilder ++= escaped
    attributes.foreach { case (key, value) =>
      flatHtmlBuilder ++= " "
      flatHtmlBuilder ++= SimpleHtmlNode.escape(key)
      value match {
        case Some(g) =>
          flatHtmlBuilder ++= "=\""
          flatHtmlBuilder ++= SimpleHtmlNode.escape(g)
          flatHtmlBuilder ++= "\""
        case None    => // nothing
      }
    }
    flatHtmlBuilder ++= ">"
    if (comment.nonEmpty) {
      flatHtmlBuilder ++= "<!--"
      flatHtmlBuilder ++= comment.replace("-->", "")
      flatHtmlBuilder ++= "-->"
    }
    children.foreach(_.flatToBuilder(flatHtmlBuilder))
    flatHtmlBuilder ++= "</"
    flatHtmlBuilder ++= escaped
    flatHtmlBuilder ++= ">"
  }
}

sealed trait SimpleHtmlNode {
  def embeddedComponents: Iterable[Component]

  def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit
}

object SimpleHtmlNode {
  case class Text(text: String) extends SimpleHtmlNode {
    override def embeddedComponents: Iterable[Component] = Iterable.empty

    override def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit = {
      flatHtmlBuilder ++= escape(text)
    }
  }

  case class Wrapper(html: Html) extends SimpleHtmlNode {
    override def embeddedComponents: Iterable[Component] = html.embeddedComponents

    override def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit = {
      html.flatToBuilder(flatHtmlBuilder)
    }
  }

  case class EmbeddedComponent(component: Component) extends SimpleHtmlNode {
    def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit = flatHtmlBuilder.addPlaceholder(component.id)

    def embeddedComponents: Iterable[Component] = List(component)
  }

  def escape(s: String): String = {
    s
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#039;")
  }
}
