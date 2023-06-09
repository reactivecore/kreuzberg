package kreuzberg

import kreuzberg.*
import kreuzberg.dom.{ScalaJsElement, ScalaJsEvent}

/**
 * A Component base which uses a more imperative [[AssemblerContext]] for collecting event handlers and model
 * subscriptions.
 */
abstract class SimpleComponentBase extends SimpleContextDsl with Component {
  def assemble(using c: SimpleContext): Html

  override final def assemble(using context: AssemblerContext): Assembly = {
    val sc   = new SimpleContext(context)
    val html = assemble(using sc)
    Assembly(
      html = html,
      handlers = sc.eventBindings(),
      subscriptions = sc.subscriptions()
    )
  }
}
