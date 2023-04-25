package kreuzberg.imperative

import kreuzberg.*
import kreuzberg.util.Stateful
import kreuzberg.dom.ScalaJsElement

import scala.language.implicitConversions

/** A Simplified AssemblyContext, which also tracks Event Bindings. */
class SimpleContext(state: AssemblyState) extends AssemblyContext(state) {
  private val _eventBindings = Vector.newBuilder[EventBinding]

  def addEventBinding(binding: EventBinding): Unit = {
    _eventBindings += binding
  }

  def eventBindings(): Vector[EventBinding] = _eventBindings.result()
}

trait SimpleContextDsl extends ImperativeDsl {
  protected def add(binding0: EventBinding, others: EventBinding*)(implicit c: SimpleContext): Unit = {
    c.addEventBinding(binding0)
    others.foreach(c.addEventBinding)
  }
}

/**
 * A component base which lets the user build HTML and Elements are inserted using PlaceholderTags
 */
abstract class SimpleComponentBase extends SimpleContextDsl {
  def assemble(implicit c: SimpleContext): Html
}

object SimpleComponentBase {
  implicit def assembler[T <: SimpleComponentBase]: Assembler.Aux[T, Unit] = { value =>
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

abstract class SimpleComponentBaseWithRuntime[R] extends SimpleContextDsl {

  type HtmlWithRuntime = (Html, RuntimeProvider[R])

  def assemble(implicit c: SimpleContext): HtmlWithRuntime

  protected def jsElement[T <: ScalaJsElement](using r: RuntimeContext): T = {
    r.jsElement.asInstanceOf[T]
  }
}

object SimpleComponentBaseWithRuntime {
  implicit def assembler[R, T <: SimpleComponentBaseWithRuntime[R]]: Assembler.Aux[T, R] = { value =>
    Stateful { state =>
      implicit val sc      = new SimpleContext(state)
      val (html, provider) = value.assemble
      val placeholders     = html.placeholders.toVector
      if (placeholders.isEmpty) {
        // Naked HTML
        sc.state -> Assembly.Pure(html, sc.eventBindings(), provider)
      } else {
        val renderFn: Vector[Html] => Html = { renderedComponents =>
          placeholders.zip(renderedComponents).foreach { case (placeholder, renderedComponent) =>
            PlaceholderState.set(placeholder.id, renderedComponent)
          }
          html
        }
        sc.state -> Assembly.Container(placeholders, renderFn, sc.eventBindings(), provider)
      }
    }
  }
}
