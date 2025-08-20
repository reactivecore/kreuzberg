package kreuzberg

import kreuzberg.EventSource.ChannelSource

import scala.language.implicitConversions

/**
 * A Channel is something where you can send data to and can subscribe in event bindings. They are allowed to be
 * singletons. They are identified using their ID. There is only one channel of the same id allowed within an Engine.
 */
final class Channel[T] private {
  val id: Identifier = Identifier.next()

  override def hashCode(): Int = id.value

  override def equals(obj: Any): Boolean = {
    obj match {
      case c: Channel[_] => id == c.id
      case _             => false
    }
  }

  /** Trigger from Handler. */
  def apply(value: T): Unit = {
    KreuzbergContext.get().changer.triggerChannel(this, value)
  }

  /** Trigger from Handler (Unit or Any) */
  def apply()(using ev: Unit => T): Unit = {
    KreuzbergContext.get().changer.triggerChannel(this, ev(()))
  }
}

object Channel {

  /** Create a channel of a given type. */
  def create[T](): Channel[T] = Channel()

  implicit def toEventSource[T](channel: Channel[T]): ChannelSource[T] = ChannelSource(channel)
}
