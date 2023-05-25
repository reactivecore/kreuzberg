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

  final def assemble: AssemblyResult = {
    Stateful { state =>
      val sc   = new SimpleContext(state)
      val html = assemble(sc)
      sc.state -> Assembly(html, sc.eventBindings())
    }
  }
}
