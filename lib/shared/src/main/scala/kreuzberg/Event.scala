package kreuzberg

import kreuzberg.dom.ScalaJsEvent

/** An Event which can be triggered by some component. */
sealed trait Event[E] {
  def map[F](f: E => F): Event[F] = Event.MappedEvent(this, f)
}

object Event {

  /**
   * A JavaScript event
   * @param name
   *   name of the JS Event.
   * @param fn
   *   initial transformation (done when event occurs)
   */
  case class JsEvent[T](
      name: String,
      fn: ScalaJsEvent => T,
      capture: Boolean
  ) extends Event[T]

  object JsEvent {
    def apply(name: String, preventDefault: Boolean = false, capture: Boolean = false): JsEvent[ScalaJsEvent] = {
      JsEvent(
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

    /** A JsEvent with initial function */
    def custom[T](name: String, capture: Boolean = false)(fn: ScalaJsEvent => T): JsEvent[T] = {
      JsEvent(name, fn, capture)
    }
  }

  /** Trivial event that something assembled. */
  case object Assembled extends Event[Unit]

  /**
   * Custom Component event, which can be triggered by a component and can be subscribed by components.
   */
  case class Custom[E](
      name: String
  ) extends Event[E]

  /** Maps event data. */
  case class MappedEvent[E, F](
      underlying: Event[E],
      mapFn: E => F
  ) extends Event[F]
}
