package kreuzberg

import kreuzberg.Identifier

/**
 * A JavaScript DOM Event
 *
 * @param componentId
 *   the component ID, if not given, it's a window event.
 * @param name
 *   name of the event
 * @param preventDefault
 *   if true, preventDefault will be called on it
 * @param capture
 *   if true, the event will be captured.
 */
case class JsEvent(
    componentId: Option[Identifier],
    name: String,
    preventDefault: Boolean = false,
    capture: Boolean = false
)
