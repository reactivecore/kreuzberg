package kreuzberg

import kreuzberg.SimpleHtmlNode.Wrapper

import scala.annotation.static
import scala.collection.immutable.Vector

/** Trivial implementation of Html. Note: just implemented enough to get it barely working. */
case class SimpleHtml(
    tag: String,
    attributes: Vector[(String, Option[String])] = Vector.empty,
    children: Vector[SimpleHtmlNode] = Vector.empty
) extends Html
    with SimpleHtmlNode {
  override def withId(id: ComponentId): Html = {
    withAttribute("data-id", Some(id.toString))
  }

  def withAttribute(name: String, value: Option[String]): Html = {
    copy(
      attributes = (attributes.view.filterNot(_._1 == name) ++ Seq(name -> value)).toVector
    )
  }

  override def addInner(inner: Seq[Html]): Html = {
    copy(
      children = children ++ inner.map(Wrapper.apply)
    )
  }

  override def placeholders: Iterable[TreeNode] = {
    children.flatMap(_.placeholders)
  }

  override def render(sb: StringBuilder): Unit = {
    val escaped = SimpleHtmlNode.escape(tag)
    sb ++= "<"
    sb ++= escaped
    attributes.foreach { case (key, value) =>
      sb ++= " "
      sb ++= SimpleHtmlNode.escape(key)
      value match {
        case Some(g) =>
          sb ++= "=\""
          sb ++= SimpleHtmlNode.escape(g)
          sb ++= "\""
        case None    => // nothing
      }
    }
    sb ++= ">"
    children.foreach(_.render(sb))
    sb ++= "</"
    sb ++= escaped
    sb ++= ">"
  }
}

sealed trait SimpleHtmlNode {
  def placeholders: Iterable[TreeNode]

  def render(sb: StringBuilder): Unit
}

object SimpleHtmlNode {
  case class Text(text: String) extends SimpleHtmlNode {
    override def placeholders: Iterable[TreeNode] = Iterable.empty

    override def render(sb: StringBuilder): Unit = sb ++= escape(text)
  }

  case class Wrapper(html: Html) extends SimpleHtmlNode {
    override def placeholders: Iterable[TreeNode] = html.placeholders

    override def render(sb: StringBuilder): Unit = html.render(sb)
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
