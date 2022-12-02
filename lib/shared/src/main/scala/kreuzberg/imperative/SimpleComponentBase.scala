package kreuzberg.imperative

import kreuzberg.*
import kreuzberg.util.Stateful

import scala.language.implicitConversions

/** A Simplified AssemblyContext, which also tracks Event Bindings. */
class SimpleContext(state: AssemblyState) extends AssemblyContext(state) {
  private val _eventBindings = Vector.newBuilder[EventBinding]

  def addEventBinding(binding: EventBinding): Unit = {
    _eventBindings += binding
  }

  def eventBindings(): Vector[EventBinding] = _eventBindings.result()
}

/**
 * A component base which lets the user build HTML and Elements are inserted using PlaceholderTags
 */
abstract class SimpleComponentBase extends ImperativeDsl {
  def assemble(implicit c: SimpleContext): Html

  protected def add[E](source: EventSource[E], sink: EventSink[E])(implicit c: SimpleContext): Unit = {
    add(EventBinding(source, sink))
  }

  protected def add(binding: EventBinding)(implicit c: SimpleContext): Unit = {
    c.addEventBinding(binding)
  }
}

object SimpleComponentBase {
  implicit def assembler[T <: SimpleComponentBase]: Assembler[T] = { value =>
    Stateful { state =>
      implicit val sc  = new SimpleContext(state)
      val html         = value.assemble
      val placeholders = html.placeholders.toVector
      if (placeholders.isEmpty) {
        // Naked HTML
        sc.state -> Assembly.Pure(html, sc.eventBindings())
      } else {
        val renderFn: Vector[Html] => Html = { renderedComponents =>
          placeholders.zip(renderedComponents).foreach { case (placeholder, renderedComponent) =>
            PlaceholderState.set(placeholder.id, renderedComponent)
          }
          html
        }
        sc.state -> Assembly.Container(placeholders, renderFn, sc.eventBindings())
      }
    }
  }
}
