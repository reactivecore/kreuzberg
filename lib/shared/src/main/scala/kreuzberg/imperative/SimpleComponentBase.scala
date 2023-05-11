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
abstract class SimpleComponentBase extends SimpleContextDsl with Component {
  def assemble(implicit c: SimpleContext): Html

  type Runtime = Unit

  final def assemble: AssemblyResult[Runtime] = {
    Stateful { state =>
      val sc   = new SimpleContext(state)
      val html = assemble(sc)
      sc.state -> Assembly(html, sc.eventBindings())
    }
  }
}

abstract class SimpleComponentBaseWithRuntime[R] extends SimpleContextDsl with Component {
  type Runtime = R

  type HtmlWithRuntime = (Html, RuntimeProvider[R])

  def assemble(implicit c: SimpleContext): HtmlWithRuntime

  protected def jsElement[T <: ScalaJsElement](using r: RuntimeContext): T = {
    r.jsElement.asInstanceOf[T]
  }

  final def assemble: AssemblyResult[R] = {
    Stateful { state =>
      val sc               = new SimpleContext(state)
      val (html, provider) = assemble(sc)
      sc.state -> Assembly(html, sc.eventBindings(), provider)
    }
  }
}
