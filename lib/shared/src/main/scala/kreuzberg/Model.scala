package kreuzberg

/** Something which can be subscribed. */
sealed trait Subscribeable[+T] {

  def initial(using ServiceRepository): T

  /** Read the current value. */
  def read()(using mvp: ModelValueProvider): T = mvp.value(this)

  /** Subscribe to this Value, to be used in [[SimpleComponentBase]] */
  def subscribe()(using sc: SimpleContext): T = {
    sc.addSubscription(this)
    sc.value(this)
  }

  /** Map a subscribable value to something else. */
  def map[U](fn: T => U): Subscribeable[U] = {
    Model.Mapped(this, fn)
  }

  def dependencies: Seq[Identifier]
}

object Subscribeable {

  /** Implicit conversion from constant values. */
  import scala.language.implicitConversions
  implicit def fromValue[T](value: T): Subscribeable[T] = {
    Model.constant(value)
  }
}

/**
 * A Model is something which holds a value and can be subscribed to. In contrast to channels, models carry a current
 * value and are subscribed by components. They are allowed to be singletons. They are identified using their ID. There
 * is only one model of the same id allowed within an Engine.
 */
final class Model[T] private (initialValue: ServiceRepository ?=> T) extends Subscribeable[T] with Identified {
  val id: Identifier = Identifier.next()

  override def hashCode(): Int = id.value

  override def equals(obj: Any): Boolean = {
    obj match {
      case c: Model[_] => id == c.id
      case _           => false
    }
  }

  override def toString: String = {
    s"M${id.value}"
  }

  override def initial(using ServiceRepository): T = initialValue

  override def dependencies: Seq[Identifier] = Seq(id)

  /** Set a value from an Handler. */
  def set(value: T)(using c: Changer): Unit = {
    c.updateModel(this, _ => value)
  }

  /** Update a model from handler. */
  def update(f: T => T)(using c: Changer): Unit = {
    c.updateModel(this, f)
  }
}

object Model {

  case class Mapped[+U, T](
      underlying: Subscribeable[T],
      fn: T => U
  ) extends Subscribeable[U] {

    override def initial(using ServiceRepository): U = fn(underlying.initial)

    override def dependencies: Seq[Identifier] = underlying.dependencies
  }

  case class Constant[+T](
      value: T
  ) extends Subscribeable[T] {

    override def subscribe()(using sc: SimpleContext): T = {
      value
    }

    override def initial(using ServiceRepository): T = value

    override def dependencies: Seq[Identifier] = Nil
  }

  /** Create a constant value */
  inline def constant[T](value: T): Constant[T] = Constant(value)

  /** Create a model. */
  def create[T](initialValue: ServiceRepository ?=> T): Model[T] = Model(initialValue)
}
