package kreuzberg

import kreuzberg.RuntimeState.JsProperty

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import kreuzberg.dom.{ScalaJsElement, ScalaJsEvent}

import scala.annotation.targetName
import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.duration.FiniteDuration
import scala.ref.WeakReference
import scala.util.Failure
import scala.util.Success

/** A Source of an [[EventBinding]]. */
sealed trait EventSource[+E] {

  /** Combine with some other event source. */
  def or[T >: E](source: EventSource[T]): EventSource[T] = EventSource.OrSource(this, source)

  /** Attach a handler */
  def handle[T >: E](f: T => HandlerContext ?=> Unit): EventBinding[T] = {
    EventBinding(
      this,
      EventSink(f)
    )
  }

  def handleAny(f: HandlerContext ?=> Unit): EventBinding[Any] = {
    handle(_ => f)
  }

  /** Attach a handler. */
  def to[T >: E](sink: EventSink[T]): EventBinding[T] = {
    EventBinding(this, sink)
  }

  def map[F](f: E => F): EventSource[F] = {
    EventSource.Transformer(this, e => List(f(e)))
  }

  def filter(f: E => Boolean): EventSource[E] = {
    EventSource.Transformer(this, e => if (f(e)) Nil else List(e))
  }

  def collect[F](f: PartialFunction[E, F]): EventSource[F] = {
    EventSource.Transformer(
      this,
      e => {
        if (f.isDefinedAt(e)) {
          List(f(e))
        } else {
          Nil
        }
      }
    )
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

  /** Transforms event sources */
  case class Transformer[E, F](
      from: EventSource[E],
      f: E => Seq[F]
  ) extends EventSource[F]
}

/** A Sink of events */
case class EventSink[E](f: (HandlerContext, E) => Unit) {

  def trigger(value: E)(using h: HandlerContext): Unit = {
    h.triggerSink(this, value)
  }

}

object EventSink {
  def apply[E](f: E => HandlerContext ?=> Unit): EventSink[E] = {
    EventSink((c, v) => f(v)(using c))
  }
}

/** An event binding. */
case class EventBinding[E](source: EventSource[E], sink: EventSink[E])
