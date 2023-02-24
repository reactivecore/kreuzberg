package kreuzberg

import kreuzberg.Event.JsEvent

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import kreuzberg.dom.ScalaJsEvent
sealed trait EventSource[E] {

  /** Extend runtime state to an event. */
  def addState[T, S](from: ComponentNode[T])(f: T => StateGetter[S]): EventSource[(E, S)] =
    EventSource.WithState(this, from.id, f(from.value))

  /** Replace event state with view state. */
  def withState[T, S](from: ComponentNode[T])(f: T => StateGetter[S]): EventSource[S] =
    addState(from)(f).map(_._2)

  def map[F](f: E => F): EventSource[F] = EventSource.MapSource(this, f)

  def effect[F](op: EffectOperation[E, F]): EventSource.EffectEvent[E, F] = EventSource.EffectEvent(this, op)

  def effect[F](f: E => Future[F]): EventSource.EffectEvent[E, F] = EventSource.EffectEvent(this, EffectOperation((e, _) => f(e)))

  /** Shortcut for building event bindings */
  def changeModel[M](model: Model[M])(f: (E, M) => M): EventBinding.SourceSink[E] = {
    val sink = EventSink.ModelChange(model, f)
    EventBinding(this, sink)
  }

  /** Change model without caring about the value of the event. */
  def changeModelDirect[M](model: Model[M])(f: M => M): EventBinding.SourceSink[E] = {
    val sink = EventSink.ModelChange(model, (_, m) => f(m))
    EventBinding(this, sink)
  }

  /** Change model without caring about the previous value of the model. */
  def intoModel[M](model: Model[M])(f: E => M): EventBinding.SourceSink[E] = {
    changeModel(model)((e, _) => f(e))
  }

  /** Change model without caring about the previous value of the model. */
  def intoModel(model: Model[E]): EventBinding.SourceSink[E] = {
    changeModel(model)((e, _) => e)
  }

  /** Trigger some component event. */
  def trigger(ce: Event.ComponentEvent[E]): EventBinding.SourceSink[E] = {
    EventBinding(this, EventSink.TriggerComponentEvent(ce))
  }

  /** Connect this source to a sink */
  def to(sink: EventSink[E]): EventBinding.SourceSink[E] = EventBinding.SourceSink(this, sink)
}

object EventSource {

  /** An Event from some other's component representation. */
  case class RepEvent[T, E](rep: ComponentNode[T], event: Event[E]) extends EventSource[E]

  /** An Event from the own component. */
  case class OwnEvent[E](event: Event[E]) extends EventSource[E]

  /** A JS Event from window-Object */
  case class WindowJsEvent(js: JsEvent) extends EventSource[ScalaJsEvent]

  /** Some side effect operatio (e.g. API Call) */
  case class EffectEvent[E, F](
      trigger: EventSource[E],
      effectOperation: EffectOperation[E, F]
  ) extends EventSource[Try[F]]

  /** Extend with runtime state. */
  case class WithState[E, F](
      inner: EventSource[E],
      from: ComponentId,
      getter: StateGetter[F]
  ) extends EventSource[(E, F)]

  case class ModelChange[M](
      model: Model[M]
  ) extends EventSource[(M, M)]

  case class MapSource[E, F](
      from: EventSource[E],
      fn: E => F
  ) extends EventSource[F]

  /** Pseudo Event source, to chain multiple reactions on one source. */
  case class AndSource[E](
      binding: EventBinding.SourceSink[E]
  ) extends EventSource[E]
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

  /** Execute some custom Code */
  case class Custom[E](f: E => Unit) extends EventSink[E]

  /** Chain multiple sinks. */
  case class Multiple[E](sinks: Vector[EventSink[E]]) extends EventSink[E]

  /** Trigger a component event. */
  case class TriggerComponentEvent[E](componentEvent: Event.ComponentEvent[E]) extends EventSink[E]
}

sealed trait EventBinding

object EventBinding {

  /** Binds some source event to sink event. */
  case class SourceSink[E](
      source: EventSource[E],
      sink: EventSink[E]
  ) extends EventBinding {

    /** Helper for adding more sinks on one source. */
    def and: EventSource.AndSource[E] = EventSource.AndSource(this)
  }

  def apply[E](
      source: EventSource[E],
      sink: EventSink[E]
  ): SourceSink[E] = SourceSink(source, sink)

  def customOwn[T](
      name: String,
      preventDefault: Boolean = false,
      capture: Boolean = false
  )(
      f: ScalaJsEvent => Unit
  ): EventBinding = {
    EventBinding(
      EventSource.OwnEvent[ScalaJsEvent](
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
