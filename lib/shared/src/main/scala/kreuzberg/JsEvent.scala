package kreuzberg

import kreuzberg.Identifier
import kreuzberg.dom.ScalaJsEvent

/**
 * A JavaScript DOM Event (not yet ready)
 *
 * @param component
 *   the component, if not given, it's a window event.
 * @param name
 *   name of the event
 * @param fn
 *   initial transformation function (executed directly in event handler, es needed e.g. for Transformation Events)
 * @param capture
 *   if true, the event will be captured.
 */
case class JsEvent[T](
    component: Option[Identifier],
    name: String,
    fn: ScalaJsEvent => T,
    capture: Boolean
)

object JsEvent {

  def apply(
      component: Option[Identifier],
      name: String,
      preventDefault: Boolean = false,
      capture: Boolean = false
  ): JsEvent[ScalaJsEvent] = {
    JsEvent(
      component,
      name,
      e => {
        if (preventDefault) {
          e.preventDefault()
        }
        e
      },
      capture
    )
  }

  def custom[T](component: Option[Identifier], name: String, capture: Boolean = false)(
      fn: ScalaJsEvent => T = identity
  ): JsEvent[T] = JsEvent(component, name, fn, capture)
}
