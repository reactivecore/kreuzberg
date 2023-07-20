package kreuzberg

import kreuzberg.RuntimeState.JsProperty

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import kreuzberg.dom.{ScalaJsElement, ScalaJsEvent}

/** DSL For Building Event Sources. */
trait EventSourceDsl[+E] extends EventSourceDslModifiers[E] with EventSourceActions[E] {
  self: EventSource[E] =>
}

/** Methods which chain event sources. */
trait EventSourceDslModifiers[+E] {
  self: EventSource[E] =>

  // Methods adding something to the event source
  // `with` replaces event data, while non-with-methods append.

  /** Add some runtime state. */
  inline def state[S](from: RuntimeState[S]): EventSource[(E, S)] = {
    EventSource.WithState(this, from)
  }

  /** Replace with runtime state. */
  inline def withState[S](from: RuntimeState[S]): EventSource[S] = state(from).map(_._2)

  /** Add some effect. */
  inline def effect[T >: E, R](op: T => Effect[R]): EventSource[(T, Try[R])] = {
    EventSource.EffectEvent(this, op)
  }

  /** Replace with effect data. */
  inline def withEffect[T >: E, R](op: T => Effect[R]): EventSource[Try[R]] = effect(op).map(_._2)

  /** Add some future. */
  inline def future[T >: E, R](op: T => Future[R]): EventSource[(T, Try[R])] = {
    effect(in => Effect.future(_ => op(in)))
  }

  /** Replace with future result. */
  inline def withFuture[T >: E, R](op: T => Future[R]): EventSource[Try[R]] = future(op).map(_._2)

  // Transforming functions
  def map[F](f: E => F): EventSource[F] = EventSource.MapSource(this, f)

  def collect[F](f: PartialFunction[E, F]): EventSource[F] = EventSource.CollectEvent(this, f)

  def filter(f: E => Boolean): EventSource[E] = collect {
    case x if f(x) => x
  }

  /** Take the second value of a Pair. */
  inline def second[T >: E, A, B](using ev: T => (A, B)): EventSource[B] = {
    this.map(x => ev(x)._2)
  }

  /** Throw away any data. */
  inline def mapUnit: EventSource[Unit] = map(_ => ())

  /** Recover a Try-Event source into something useful. */
  def recoverWith[T, R](f: Throwable => T)(implicit ev: E => Try[T]): EventSource[T] = {
    map { value =>
      ev(value) match {
        case Success(value) => value
        case Failure(error) => f(error)
      }
    }
  }

  /** Recovers the most right part of a pair. */
  def recoverWith2[A, T, R](f: Throwable => T)(implicit ev: E => (A, Try[T])): EventSource[(A, T)] = {
    map { value =>
      ev(value) match
        case (x, Success(value)) => (x, value)
        case (x, Failure(error)) => (x, f(error))
    }
  }

  /** Transform via function. */
  def transform[F, R](f: EventSource[E] => R): R = f(this)

  /** If e is a Try[T], handle errors using another sink. */
  def handleErrors[F](sink: EventSink[Throwable])(implicit ev: E => Try[F]): EventSource[F] = {
    val errorHandler = sink.contraCollect[Try[F]] { case Failure(exception) =>
      exception
    }
    map(ev).to(errorHandler).and.collect { case Success(value) =>
      value
    }
  }
}

/** DSL Methods which create a Source Sink. */
trait EventSourceActions[+E] {
  self: EventSource[E] =>

  // Model modifying actions

  /** Shortcut for building event bindings */
  def changeModel[T >: E, M](model: Model[M])(f: (T, M) => M): EventBinding.SourceSink[T] = {
    val sink = EventSink.ModelChange(model, f)
    EventBinding(this, sink)
  }

  /** Change model without caring about the value of the event. */
  def changeModelDirect[T >: E, M](model: Model[M])(f: M => M): EventBinding.SourceSink[T] = {
    val sink = EventSink.ModelChange[T, M](model, (_, m) => f(m))
    EventBinding(this, sink)
  }

  /** Set the model to a value without caring about the value of the event or model before */
  def setModel[T >: E, M](model: Model[M], value: M): EventBinding.SourceSink[T] = {
    val sink = EventSink.ModelChange(model, (_, _) => value)
    EventBinding(this, sink)
  }

  /** Change model without caring about the previous value of the model. */
  def intoModel[T >: E, M](model: Model[M])(f: T => M): EventBinding.SourceSink[T] = {
    changeModel(model)((e, _) => f(e))
  }

  /** Change model without caring about the previous value of the model. */
  def intoModel[T >: E](model: Model[T]): EventBinding.SourceSink[T] = {
    changeModel(model)((e, _) => e)
  }

  /** Execute custom Code. */
  def executeCode[T >: E](f: T => Unit): EventBinding.SourceSink[T] = {
    EventBinding.SourceSink(this, EventSink.ExecuteCode(f))
  }

  /**
   * Do nothing (e.g. some events already have an effect just because they are registered, e.g. Drag and Drop events
   * with preventDefault)
   */
  def doNothing[T >: E]: EventBinding.SourceSink[T] = {
    executeCode(_ => ())
  }

  /** Write a value into a property */
  def intoProperty[T >: E, D <: ScalaJsElement](prop: JsProperty[D, T]): EventBinding.SourceSink[T] = {
    EventBinding(this, EventSink.SetProperty(prop))
  }

  /** Trigger some channel. */
  def trigger[T >: E](channel: Channel[T]): EventBinding.SourceSink[T] = {
    EventBinding(
      this,
      EventSink.ChannelSink(channel)
    )
  }

  /** Connect this source to a sink */
  def to[T >: E](sink: EventSink[T]): EventBinding.SourceSink[T] = EventBinding.SourceSink(this, sink)

  /** Combine with some other event source. */
  def or[T >: E](source: EventSource[T]): EventSource[T] = EventSource.OrSource(this, source)

  /** Execute code while traversing the source. */
  def tap[T >: E](fn: T => Unit): EventSource[T] = EventSource.TapSource(this, fn)
}
