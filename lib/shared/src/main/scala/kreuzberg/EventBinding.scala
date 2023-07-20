package kreuzberg

import kreuzberg.RuntimeState.JsProperty

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import kreuzberg.dom.{ScalaJsElement, ScalaJsEvent}

import scala.concurrent.duration.FiniteDuration
import scala.ref.WeakReference
import scala.util.Failure
import scala.util.Success

/** A Source of an [[EventBinding]]. */
sealed trait EventSource[+E] extends EventSourceDsl[E]

object EventSource {

  /** JS Event */
  case class Js[E](jsEvent: JsEvent[E]) extends EventSource[E]

  object Js {
    def window(name: String, preventDefault: Boolean = false, capture: Boolean = false): Js[ScalaJsEvent] = Js(
      JsEvent(None, name, preventDefault, capture)
    )
  }

  /** Object got assembled. */
  case object Assembled extends EventSource[Unit]

  case class ChannelSource[E](channel: WeakReference[Channel[E]]) extends EventSource[E]

  object ChannelSource {
    inline def apply[E](channel: Channel[E]): ChannelSource[E] = ChannelSource[E](WeakReference(channel))
  }

  /** Some side effect operation (e.g. API Call) */
  case class EffectEvent[E, R](
      trigger: EventSource[E],
      effectOperation: E => Effect[R]
  ) extends EventSource[(E, Try[R])]

  /** Add some component state to the Event. */
  case class WithState[E, S](
      inner: EventSource[E],
      runtimeState: RuntimeState[S]
  ) extends EventSource[(E, S)]

  case class MapSource[E, F](
      from: EventSource[E],
      fn: E => F
  ) extends EventSource[F]

  /** Some collect function. */
  case class CollectEvent[E, F](
      from: EventSource[E],
      fn: PartialFunction[E, F]
  ) extends EventSource[F]

  /** Pseudo Event source, to chain multiple reactions on one source. */
  case class AndSource[E](
      binding: EventBinding.SourceSink[E]
  ) extends EventSource[E]

  case class OrSource[E](
      left: EventSource[E],
      right: EventSource[E]
  ) extends EventSource[E]

  case class TapSource[E](
      inner: EventSource[E],
      fn: E => Unit
  ) extends EventSource[E]

  /** A Timer. */
  case class Timer(
      duration: FiniteDuration,
      periodic: Boolean = false
  ) extends EventSource[Unit]
}

/** Sink of an [[EventBinding]] */
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

  /** Applies a partial function before calling the sink. */
  def contraCollect[F](pf: PartialFunction[F, E]): EventSink[F] = EventSink.ContraCollect(this, pf)

  /** Applies a map function before calling a sink. */
  def contraMap[F](f: F => E): EventSink[F] = EventSink.ContraMap(this, f)
}

object EventSink {

  /** Issue a model change */
  case class ModelChange[E, M](model: Model[M], f: (E, M) => M) extends EventSink[E]

  /** Execute some custom Code */
  case class ExecuteCode[E](f: E => Unit) extends EventSink[E]

  /** Chain multiple sinks. */
  case class Multiple[E](sinks: Vector[EventSink[E]]) extends EventSink[E]

  /** Applies a partial function before calling the sink. */
  case class ContraCollect[E, F](sink: EventSink[E], pf: PartialFunction[F, E]) extends EventSink[F]

  /** Applies a map function before calling a sink. */
  case class ContraMap[E, F](sink: EventSink[E], f: F => E) extends EventSink[F]

  /** Trigger a Channel. */
  case class ChannelSink[E](channel: WeakReference[Channel[E]]) extends EventSink[E]

  /** Set a javascript property */
  case class SetProperty[D <: ScalaJsElement, S](property: JsProperty[D, S]) extends EventSink[S]

  object ChannelSink {
    inline def apply[E](channel: Channel[E]): ChannelSink[E] = ChannelSink(WeakReference(channel))
  }
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
}
