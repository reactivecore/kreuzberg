package kreuzberg

import kreuzberg.Event.JsEvent
import org.scalajs.dom.Event as ScalaJsEvent

sealed trait EventSource[E] {

  /** Extend runtime state to an event. */
  def withState[T, S](from: Rep[T])(f: T => StateGetter[S]): EventSource[(E,S)] = EventSource.WithState(this, from.id, f(from.value))

  /** Replace event state with view state. */
  def withReplacedState[T, S](from: Rep[T])(f: T => StateGetter[S]): EventSource[S] = withState(from)(f).map(_._2)

  def map[F](f: E => F): EventSource[F] = EventSource.MapSource(this, f)

  /** Shortcut for building event bindings */
  def toModel[M](model: Model[M])(f: (E, M) => M): EventBinding.SourceSink[E] = {
    val sink = EventSink.ModelChange(model, f)
    EventBinding(this, sink)
  }

  /** Change model without caring about the value of the event. */
  def toModelChange[M](model: Model[M])(f: M => M): EventBinding.SourceSink[E] = {
    val sink = EventSink.ModelChange(model, (_, m) => f(m))
    EventBinding(this, sink)
  }
}

object EventSource {

  /** An Event from some other's component representation. */
  case class RepEvent[T, E](rep: Rep[T], event: Event[E]) extends EventSource[E]

  /** An Event from the own component. */
  case class OwnEvent[E](event: Event[E]) extends EventSource[E]

  /** A JS Event from window-Object */
  case class WindowJsEvent(js: JsEvent) extends EventSource[ScalaJsEvent]

  /** Extend with runtime state. */
  case class WithState[E, F](
      inner: EventSource[E],
      from: ComponentId,
      getter: StateGetter[F]
  ) extends EventSource[(E, F)]

  case class BusEvent[E](
      bus: Bus[E]
  ) extends EventSource[E]

  case class MapSource[E, F](
      from: EventSource[E],
      fn: E => F
  ) extends EventSource[F]
}

sealed trait EventSink[-E] {

  /** Add another sink */
  def and[T <: E](sink: EventSink[T]): EventSink[T] = {
    this match {
      case m: EventSink.Multiple[_] =>
        m.copy(m.sinks :+ sink)
      case other                    =>
        EventSink.Multiple(Vector(this, sink))
    }
  }
}

object EventSink {

  /** Issue a model change */
  case class ModelChange[E, M](model: Model[M], f: (E, M) => M) extends EventSink[E]

  /** Call a Message Bus */
  case class BusCall[E](bus: Bus[E]) extends EventSink[E]

  /** Execute some custom Code */
  case class Custom[E](f: E => Unit) extends EventSink[E]

  /** Chain multiple sinks. */
  case class Multiple[E](sinks: Vector[EventSink[E]]) extends EventSink[E]
}

sealed trait EventBinding

object EventBinding {

  /** Binds some source event to sink event. */
  case class SourceSink[E](
      source: EventSource[E],
      sink: EventSink[E]
  ) extends EventBinding

  def apply[E](
      source: EventSource[E],
      sink: EventSink[E]
  ): SourceSink[E] = SourceSink(source, sink)

  def customOwn[T](
      name: String,
      preventDefault: Boolean = false,
      capture: Boolean = false
  )(
      f: org.scalajs.dom.Event => Unit
  ): EventBinding = {
    EventBinding(
      EventSource.OwnEvent[org.scalajs.dom.Event](
        Event.JsEvent(
          name,
          preventDefault,
          capture
        )
      ),
      EventSink.Custom(f)
    )
  }
}
