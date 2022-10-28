package kreuzberg

import org.scalajs.dom.Element as JsElement
import scalatags.Text.TypedTag

/** Return result of an assembly operation (will be build into a Representation) */
sealed trait Assembly {
  def map(f: Html => Html): Assembly = {
    this match
      case p: Assembly.Pure      => p.copy(html = f(p.html))
      case c: Assembly.Container =>
        c.copy(
          renderer = parts => f(c.renderer(parts))
        )
  }

  def bindings: Vector[EventBinding]

  def nodes: Vector[Node]
}

object Assembly {
  implicit def apply(html: Html): Pure = Pure(html)

  /** Simple puts the nodes as base of the current html */
  def apply(base: Html, nodes: Vector[Node], handlers: Vector[EventBinding] = Vector.empty): Container = {
    Container(nodes, htmls => base(htmls: _*), handlers)
  }

  /** Has a raw HTML representation. */
  case class Pure(html: Html, bindings: Vector[EventBinding] = Vector.empty) extends Assembly {
    override def nodes: Vector[Node] = Vector.empty
  }

  /** Is a container with sub nodes. */
  case class Container(
      nodes: Vector[Node],
      renderer: Vector[Html] => Html,
      bindings: Vector[EventBinding] = Vector.empty
  ) extends Assembly
}

/** Represents an assembled node in a tree. */
sealed trait Node {
  def id: ComponentId
  def assembly: Assembly

  /** Returns children nodes. */
  def children: Vector[Node] = assembly.nodes
}

/** A Representation of the component. */
case class Rep[T](
    id: ComponentId,
    value: T,
    assembly: Assembly,
    assembler: Assembler[T]
) extends Node {
  /** Build an event source from this element. */
  def event[E](f: T => Event[E]): EventSource.RepEvent[T, E] = EventSource.RepEvent(this, f(value))
}
