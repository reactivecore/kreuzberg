package kreuzberg.imperative

import kreuzberg.*
import kreuzberg.util.Stateful
import kreuzberg.dom.{ScalaJsElement, ScalaJsEvent}

import scala.language.implicitConversions

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
    r.jsElement(id).asInstanceOf[T]
  }

  final def assemble: AssemblyResult[R] = {
    Stateful { state =>
      val sc               = new SimpleContext(state)
      val (html, provider) = assemble(sc)
      sc.state -> Assembly(html, sc.eventBindings(), provider)
    }
  }
}
