package kreuzberg

import kreuzberg.RuntimeState.JsProperty

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import kreuzberg.dom.{ScalaJsElement, ScalaJsEvent}

import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.duration.FiniteDuration
import scala.ref.WeakReference
import scala.util.Failure
import scala.util.Success

trait EventTransformable[+E] extends EventTransformationDsl[E] with EventSinkApplicationDsl[E]

/** A Source of an [[EventBinding]]. */
sealed trait EventSource[+E] extends EventTransformable[E] {
  override type WithTransformer[F] = EventSource[F]
  override type WithSink[G]        = EventBinding.SourceSink[G]

  override def withTransformer[Q](transformer: EventTransformer[E, Q]): EventSource[Q] = {
    EventSource.PostTransformer(this, transformer)
  }

  /** Transform via function. */
  inline def transform[R](f: EventSource[E] => R): R = f(this)

  /** Combine with some other event source. */
  def or[T >: E](source: EventSource[T]): EventSource[T] = EventSource.OrSource(this, source)

  override def to[T >: E](sink: EventSink[T]): EventBinding.SourceSink[T] = {
    EventBinding.SourceSink(this, sink)
  }
}

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

  case class OrSource[E](
      left: EventSource[E],
      right: EventSource[E]
  ) extends EventSource[E]

  /** A Timer. */
  case class Timer(
      duration: FiniteDuration,
      periodic: Boolean = false
  ) extends EventSource[Unit]

  /** Transforms an event source. */
  case class PostTransformer[E, O](
      inner: EventSource[E],
      transformer: EventTransformer[E, O]
  ) extends EventSource[O]
}

sealed trait EventTransformer[-I, +O] extends EventTransformable[O] {

  /** Transform via function. */
  inline def transform[R](f: EventTransformer[I, O] => R): R = f(this)

  override final type WithTransformer[X] = EventTransformer[I @uncheckedVariance, X]
  override final type WithSink[X]        = EventSink[I @uncheckedVariance]

  /** Transforms using a Transformer. */

  override def withTransformer[Q](transformer: EventTransformer[O, Q]): EventTransformer[I, Q] = {
    EventTransformer.Chained[I, O, Q](this, transformer)
  }

  override def to[T >: O](sink: EventSink[T]): EventSink[I] = {
    EventSink.PreTransformer(sink, this)
  }

  def viaSink(sink: EventSink[O]): EventTransformer[I, O] = {
    EventTransformer.Chained(this, EventTransformer.And(sink))
  }
}

object EventTransformer {
  // Simple Transformations
  case class Map[I, O](fn: I => O)                                extends EventTransformer[I, O]
  case class Collect[I, O](fn: PartialFunction[I, O])             extends EventTransformer[I, O]
  case class Tapped[I](fn: I => Unit)                             extends EventTransformer[I, I]
  // Adding Runtime State
  case class AddState[I, S](runtimeState: RuntimeState[S])        extends EventTransformer[I, (I, S)]
  // Adding an Effect
  case class AddEffect[I, E](effectFn: I => Effect[E])            extends EventTransformer[I, (I, Try[E])]
  // Removing the Failure part of trys
  case class TryUnpack1[E](failure: EventSink[Throwable])         extends EventTransformer[Try[E], E]
  case class TryUnpack2[I, E](failure: EventSink[(I, Throwable)]) extends EventTransformer[(I, Try[E]), (I, E)]
  // Call other sinks, used for fan out.
  case class And[I](other: EventSink[I])                          extends EventTransformer[I, I]

  /** Empty, Starting point for transformations. */
  case class Empty[I]() extends EventTransformer[I, I] {
    override def withTransformer[Q](transformer: EventTransformer[I, Q]): EventTransformer[I, Q] = {
      transformer
    }
  }

  case class Chained[I, X, O](a: EventTransformer[I, X], b: EventTransformer[X, O]) extends EventTransformer[I, O]
}

/** Sink of an [[EventBinding]] */
sealed trait EventSink[-E] {

  /** Add another sink */
  inline def and[T <: E](sink: EventSink[T]): EventSink[T] = {
    preTransform(
      EventTransformer.And(this)
    )
  }

  inline def preTransform[F, T <: E](transformer: EventTransformer[F, T]): EventSink[F] = {
    EventSink.PreTransformer(this, transformer)
  }

  /** Applies a partial function before calling the sink. */
  inline def contraCollect[F](pf: PartialFunction[F, E]): EventSink[F] = {
    preTransform(EventTransformer.Collect(pf))
  }

  /** Applies a map function before calling a sink. */
  def contraMap[F](f: F => E): EventSink[F] = {
    preTransform(EventTransformer.Map(f))
  }
}

object EventSink {

  /** Issue a model change */
  case class ModelChange[E, M](model: Model[M], f: (E, M) => M) extends EventSink[E]

  /** Execute some custom Code */
  case class ExecuteCode[E](f: E => Unit) extends EventSink[E]

  /** Trigger a Channel. */
  case class ChannelSink[E](channel: WeakReference[Channel[E]]) extends EventSink[E]

  /** Set a javascript property */
  case class SetProperty[D <: ScalaJsElement, S](property: JsProperty[D, S]) extends EventSink[S]

  /** Pretransformes a sink. */
  case class PreTransformer[E, F](sink: EventSink[F], transformer: EventTransformer[E, F]) extends EventSink[E]

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
    inline def and: EventSource[E] = source.withTransformer(
      EventTransformer.And(sink)
    )
  }

  def apply[E](
      source: EventSource[E],
      sink: EventSink[E]
  ): SourceSink[E] = SourceSink(source, sink)
}
