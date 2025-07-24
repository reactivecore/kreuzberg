package kreuzberg

import org.scalajs.dom.Event

import scala.concurrent.duration.FiniteDuration
import scala.ref.WeakReference

/** A Source of an [[EventBinding]]. */
sealed trait EventSource[+E] {

  /** Combine with some other event source. */
  def or[T >: E](source: EventSource[T]): EventSource[T] = EventSource.OrSource(this, source)

  /** Attach a handler */
  def handle[T >: E](f: T => Unit): EventBinding[T] = {
    EventBinding(
      this,
      f
    )
  }

  /** Attach a handler, discarding the value. */
  def handleAny(f: => Unit): EventBinding[Any] = {
    handle(_ => f)
  }

  /** Map the value */
  def map[F](f: E => F): EventSource[F] = {
    EventSource.Transformer(this, e => List(f(e)))
  }

  /** Filters the value */
  def filter(f: E => Boolean): EventSource[E] = {
    EventSource.Transformer(this, e => if (f(e)) Nil else List(e))
  }

  /** Collect values. */
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

  /** Just hook in some code. */
  def hook(f: E => Unit): EventSource[E] = {
    map { x =>
      f(x)
      x
    }
  }
}

object EventSource {

  /** JS Event */
  case class Js[E](jsEvent: JsEvent) extends EventSource[Event]

  object Js {
    def window(name: String, capture: Boolean = false): Js[Event] = Js(
      JsEvent(None, name, capture)
    )
  }

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

/** An event binding. */
case class EventBinding[E](source: EventSource[E], sink: E => Unit)
