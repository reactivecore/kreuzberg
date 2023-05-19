package kreuzberg

import kreuzberg.util.Stateful

/**
 * A Model is something which holds a value and can be subscribed to. In contrast to channels, models carry a current
 * value and are subscribed by components. They are allowed to be singletons. They are identified using their ID. There
 * is only one model of the same id allowed within an Engine.
 */
class Model[+T] private (val initialValue: () => T) {
  val id = Identifier.next()

  override def hashCode(): Int = id.value

  override def equals(obj: Any): Boolean = {
    obj match {
      case c: Channel[_] => id == c.id
      case _             => false
    }
  }

  override def toString: String = {
    s"M${id.value}"
  }
}

object Model {

  /** Create a model. */
  def create[T](initialValue: => T): Model[T] = Model(() => initialValue)
}
