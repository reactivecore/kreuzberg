package kreuzberg

/** Something which can be subscribed. */
sealed trait Subscribeable[+T] {
  def id: Identifier

  def initial(): T
}

/**
 * A Model is something which holds a value and can be subscribed to. In contrast to channels, models carry a current
 * value and are subscribed by components. They are allowed to be singletons. They are identified using their ID. There
 * is only one model of the same id allowed within an Engine.
 */
final class Model[+T] private (val initialValue: () => T) extends Subscribeable[T] {
  val id: Identifier = Identifier.next()

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

  /** Map a model value to something else. */
  def map[U](fn: T => U): Subscribeable[U] = {
    Model.Mapped(this, fn)
  }

  override def initial(): T = initialValue()
}

object Model {

  case class Mapped[+U, T](
      underlying: Subscribeable[T],
      fn: T => U
  ) extends Subscribeable[U] {
    override def id: Identifier = underlying.id

    override def initial(): U = fn(underlying.initial())
  }

  /** Create a model. */
  def create[T](initialValue: => T): Model[T] = Model(() => initialValue)
}
