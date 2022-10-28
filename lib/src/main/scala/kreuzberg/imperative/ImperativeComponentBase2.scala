package kreuzberg.imperative

import kreuzberg._
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.text.Builder

import scala.collection.mutable

class AssemblyContext2(state: AssemblyState) extends AssemblyContext(state) {
  val eventBindings = Vector.newBuilder[EventBinding]
}

object PlaceholderState {
  private val renderings: ThreadLocal[mutable.Map[ComponentId, Html]] = new ThreadLocal[mutable.Map[ComponentId, Html]] {
    override def initialValue(): mutable.Map[ComponentId, Html] = {
      mutable.Map()
    }
  }

  def get(componentId: ComponentId): Html = {
    renderings.get().apply(componentId)
  }

  def set(componentId: ComponentId, html: Html) = {
    renderings.get().addOne(componentId, html)
  }

  def clear(): Unit = {
    renderings.get().clear()
  }
}

case class PlaceholderTag(node: Node) extends Modifier {
  override def applyTo(t: Builder): Unit = {
    PlaceholderState.get(node.id).applyTo(t)
  }
}

object PlaceholderTag {

  /** Collect extended tags inside html */
  def collectFrom(html: Html): Vector[PlaceholderTag] = {
    val result = Vector.newBuilder[PlaceholderTag]

    def walk(s: TypedTag[String]): Unit = {
      for {
        modifierBlock <- s.modifiers
        modifier      <- modifierBlock
      } {
        modifier match {
          case e: PlaceholderTag   =>
            result.addOne(e)
          case s: TypedTag[String] =>
            walk(s)
          case ignore              =>
            println(s"Ignoring tag: ${ignore} Class: ${ignore.getClass}")
        }
      }
    }

    walk(html)
    result.result()
  }
}

/** Extends ImperativeComponentBase with stateful assembly building.. */
abstract class ImperativeComponentBase2 extends ImperativeComponentBase {
  def assemble2(implicit c: AssemblyContext2): Html

  override def assemble(implicit c: AssemblyContext): Assembly = {
    implicit val context2 = new AssemblyContext2(c.state)
    val html              = assemble2
    c.transformFn(_ => (context2.state, ())) // Rescue state
    val placeholders = PlaceholderTag.collectFrom(html)
    if (placeholders.isEmpty) {
      // Naked HTML
      Assembly.Pure(html, context2.eventBindings.result())
    } else {
      val renderFn: Vector[Html] => Html = { renderedComponents =>
        placeholders.zip(renderedComponents).foreach { case (placeholder, renderedComponent) =>
          PlaceholderState.set(placeholder.node.id, renderedComponent)
        }
        html
      }
      Assembly.Container(placeholders.map(_.node), renderFn, context2.eventBindings.result())
    }
  }

  protected def add[E](source: EventSource[E], sink: EventSink[E])(implicit c: AssemblyContext2): Unit = {
    add(EventBinding(source, sink))
  }

  protected def add(binding: EventBinding)(implicit c: AssemblyContext2): Unit = {
    c.eventBindings += binding
  }

  implicit def nodeToPlaceholder(node: Node): PlaceholderTag = PlaceholderTag(node)
}
