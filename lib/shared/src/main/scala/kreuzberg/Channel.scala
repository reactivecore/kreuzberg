package kreuzberg

import scala.ref.WeakReference

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
  def trigger(value: T)(using h: HandlerContext): Unit = {
    h.triggerChannel(this, value)
  }
}

object Channel {

  /** Create a channel of a given type. */
  def create[T](): Channel[T] = Channel()
}
