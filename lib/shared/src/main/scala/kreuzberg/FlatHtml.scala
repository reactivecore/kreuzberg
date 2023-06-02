package kreuzberg

import scala.collection.mutable

sealed trait FlatHtmlElement

object FlatHtmlElement {
  case class Part(s: String)             extends FlatHtmlElement
  case class PlaceHolder(id: Identifier) extends FlatHtmlElement
}

/** Flattened HTML generated from a component for faster rerendering. */
class FlatHtml(parts: Array[FlatHtmlElement]) {

  /** Rerender Flat HTML. */
  def render(builder: StringBuilder, nodeRender: (Identifier, StringBuilder) => Unit): Unit = {
    parts.foreach {
      case FlatHtmlElement.Part(s)        => builder.append(s)
      case FlatHtmlElement.PlaceHolder(n) => nodeRender(n, builder)
    }
  }

  def renderWithoutPlaceholders(sb: StringBuilder): Unit = {
    val nodeRender: (Identifier, StringBuilder) => Unit = { (id, sb) =>
      sb ++= s"<component id=${id}/>"
    }
    render(sb, nodeRender)
  }

  def renderWithoutPlaceholders(): String = {
    val sb = StringBuilder()
    renderWithoutPlaceholders(sb)
    sb.toString()
  }

  override def toString(): String = {
    renderWithoutPlaceholders()
  }
}

object FlatHtml {
  def apply(parts: Seq[FlatHtmlElement]): FlatHtml = new FlatHtml(parts.toArray)
}
