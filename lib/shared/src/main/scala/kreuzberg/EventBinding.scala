package kreuzberg

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import kreuzberg.dom.{ScalaJsElement, ScalaJsEvent}

import scala.ref.WeakReference
import scala.util.Failure
import scala.util.Success
sealed trait EventSource[E] {

  /** Extend runtime state to event. */
  def addState[R <: ScalaJsElement, S](from: RuntimeState[S]): EventSource[(E, S)] = {
    EventSource.WithState(this, from)
  }

  /** Replaces event state with runtime state. */
  def withState[R <: ScalaJsElement, S](from: RuntimeState[S]): EventSource[S] = {
    addState(from).map(_._2)
  }

  def map[F](f: E => F): EventSource[F] = EventSource.MapSource(this, f)

  def collect[F](f: PartialFunction[E, F]): EventSource[F] = EventSource.CollectEvent(this, f)

  def filter(f: E => Boolean): EventSource[E] = collect {
    case x if f(x) => x
  }

  /** Throw away any data. */
  def mapUnit: EventSource[Unit] = map(_ => ())

  def effect[F[_], R](op: EffectOperation[E, F, R]): EventSource.EffectEvent[E, F, R] =
    EventSource.EffectEvent(this, op)

  def effect[F[_], R](f: E => F[R])(implicit effectSupport: EffectSupport[F]): EventSource.EffectEvent[E, F, R] =
    EventSource.EffectEvent(this, EffectOperation(e => f(e)))

  /** Execute custom Code. */
  def executeCode(f: E => Unit): EventBinding.SourceSink[E] = {
    EventBinding.SourceSink(this, EventSink.ExecuteCode(f))
  }

  /**
   * Do nothing (e.g. some events already have an effect just because they are registered, e.g. Drag and Drop events
   * with preventDefault)
   */
  def doNothing: EventBinding.SourceSink[E] = {
    executeCode(_ => ())
  }

  /** Shortcut for building event bindings */
  def changeModel[M](model: Model[M])(f: (E, M) => M): EventBinding.SourceSink[E] = {
    val sink = EventSink.ModelChange(model, f)
    EventBinding(this, sink)
  }

  /** Change model without caring about the value of the event. */
  def changeModelDirect[M](model: Model[M])(f: M => M): EventBinding.SourceSink[E] = {
    val sink = EventSink.ModelChange[E, M](model, (_, m) => f(m))
    EventBinding(this, sink)
  }

  /** Set the model to a value without caring about the value of the event or model before */
  def setModel[M](model: Model[M], value: M): EventBinding.SourceSink[E] = {
    val sink = EventSink.ModelChange(model, (_, _) => value)
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

  /** Trigger some channel. */
  def triggerChannel[T](channel: Channel[T]): EventBinding.SourceSink[E] = {
    EventBinding(
      this,
      EventSink.ChannelSink(channel)
    )
  }

  /** Transform via function. */
  def transform[F](f: EventSource[E] => EventSource[F]): EventSource[F] = f(this)

  /** If e is a Try[T], handle errors using another sink. */
  def handleErrors[F](sink: EventSink[Throwable])(implicit ev: E => Try[F]): EventSource[F] = {
    val errorHandler = sink.contraCollect[Try[F]] { case Failure(exception) =>
      exception
    }
    map(ev).to(errorHandler).and.collect { case Success(value) =>
      value
    }
  }

  /** Connect this source to a sink */
  def to(sink: EventSink[E]): EventBinding.SourceSink[E] = EventBinding.SourceSink(this, sink)
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

  /** Some side effect operation (e.g. API Call) */
  case class EffectEvent[E, F[_], R](
      trigger: EventSource[E],
      effectOperation: EffectOperation[E, F, R]
  ) extends EventSource[Try[R]]

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
