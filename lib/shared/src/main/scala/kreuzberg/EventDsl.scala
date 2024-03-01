package kreuzberg

import kreuzberg.RuntimeState.JsProperty

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success, Try}
import kreuzberg.dom.{ScalaJsElement, ScalaJsEvent}

/** Methods which chain event sources. */
trait EventTransformationDsl[+E] {

  /** Event Transformer applied. */
  type WithTransformer[F] <: EventTransformable[F]

  def withTransformer[Q](transformer: EventTransformer[E, Q]): WithTransformer[Q]

  // Methods adding something to the event source
  // `with` replaces event data, while non-with-methods append.

  /** Add some runtime state. */
  inline def state[F >: E, S](from: RuntimeState[S]): WithTransformer[(F, S)] = {
    withTransformer(EventTransformer.AddState(from))
  }

  /** Replace with runtime state. */
  inline def withState[S](from: RuntimeState[S]): WithTransformer[S] = {
    withTransformer(EventTransformer.AddState(from).map(_._2))
  }

  /** Add some effect. */
  inline def effect[F >: E, R](op: F => Effect[R]): WithTransformer[(F, Try[R])] = {
    withTransformer(EventTransformer.AddEffect(op))
  }

  /** Replace with effect data. */
  inline def withEffect[F >: E, R](op: F => Effect[R]): WithTransformer[Try[R]] = {
    withTransformer(EventTransformer.AddEffect(op).map(_._2))
  }

  /** Add some future. */
  inline def future[F >: E, R](op: F => ExecutionContext ?=> Future[R]): WithTransformer[(F, Try[R])] = {
    effect(in => Effect.future(op(in)))
  }

  /** Replace with future result. */
  inline def withFuture[F >: E, R](op: F => ExecutionContext ?=> Future[R]): WithTransformer[Try[R]] = {
    withTransformer(
      EventTransformer.AddEffect[F, R](in => Effect.future(op(in))).map(_._2)
    )
  }

  // Transforming functions
  inline def map[F](f: E => F): WithTransformer[F] = withTransformer(EventTransformer.Map(f))

  inline def collect[F](f: PartialFunction[E, F]): WithTransformer[F] = withTransformer(EventTransformer.Collect(f))

  inline def filter[F >: E](f: F => Boolean): WithTransformer[F] = collect {
    case x if f(x) => x
  }

  /** Take the second value of a Pair. */
  inline def second[F >: E, A, B](using ev: F => (A, B)): WithTransformer[B] = {
    map(x => ev(x)._2)
  }

  /** Throw away any data. */
  inline def mapUnit: WithTransformer[Unit] = map(_ => ())

  /** Execute code while traversing the source. */
  inline def tap[T >: E](fn: T => Unit): WithTransformer[T] = withTransformer(EventTransformer.Tapped(fn))

  /** Recover a Try-Event source into something useful. */
  inline def recoverWith[F, R](f: Throwable => F)(implicit ev: E => Try[F]): WithTransformer[F] = {
    map { value =>
      ev(value) match {
        case Success(value) => value
        case Failure(error) => f(error)
      }
    }
  }

  /** Recovers the most right part of a pair. */
  inline def recoverWith2[A, F, R](f: Throwable => F)(implicit ev: E => (A, Try[F])): WithTransformer[(A, F)] = {
    map { value =>
      ev(value) match
        case (x, Success(value)) => (x, value)
        case (x, Failure(error)) => (x, f(error))
    }
  }

  /** If e is a Try[T], handle errors using another sink. */
  inline def handleErrors[F](
      sink: EventSink[Throwable]
  )(implicit ev: E => Try[F]): WithTransformer[F] = {
    withTransformer {
      EventTransformer
        .Map(ev)
        .withTransformer(
          EventTransformer.TryUnpack1(sink)
        )
    }
  }
}

/** DSL Methods which apply a sink */
trait EventSinkApplicationDsl[+E] {
  /** A Sink applied. */
  type WithSink[G]

  // Model modifying actions

  /** Shortcut for building event bindings */
  inline def changeModel[T >: E, M](model: Model[M])(f: (T, M) => M): WithSink[T] = {
    to(EventSink.ModelChange(model, f))
  }

  /** Change model without caring about the value of the event. */
  inline def changeModelDirect[T >: E, M](model: Model[M])(f: M => M): WithSink[T] = {
    to(EventSink.ModelChange[T, M](model, (_, m) => f(m)))
  }

  /** Set the model to a value without caring about the value of the event or model before */
  inline def setModelTo[T >: E, M](model: Model[M], value: M): WithSink[T] = {
    to(EventSink.ModelChange(model, (_, _) => value))
  }

  /** Change model without caring about the previous value of the model. */
  inline def intoModel[T >: E](model: Model[T]): WithSink[T] = {
    changeModel(model)((e, _) => e)
  }

  /** Execute custom Code. */
  inline def executeCode[T >: E](f: T => Unit): WithSink[T] = {
    to(EventSink.ExecuteCode(f))
  }

  /**
   * Do nothing (e.g. some events already have an effect just because they are registered, e.g. Drag and Drop events
   * with preventDefault)
   */
  inline def doNothing[T >: E]: WithSink[T] = {
    executeCode(_ => ())
  }

  /** Write a value into a property */
  inline def intoProperty[T >: E, D <: ScalaJsElement](prop: JsProperty[D, T]): WithSink[T] = {
    to(EventSink.SetProperty(prop))
  }

  /** Trigger some channel. */
  inline def trigger[T >: E](channel: Channel[T]): WithSink[T] = {
    to(EventSink.ChannelSink(channel))
  }

  /** Connect this source to a sink */
  def to[T >: E](sink: EventSink[T]): WithSink[T]
}
