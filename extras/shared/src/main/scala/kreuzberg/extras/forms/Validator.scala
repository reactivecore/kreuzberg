package kreuzberg.extras.forms

import kreuzberg.extras.forms.Error.ValidationError

/** Simple validators */
trait Validator[-T] {

  /** Validate some value, returns list of violations. */
  def validate(value: T): Option[ValidationError]

  def validated[U <: T](value: U): Result[_] = validate(value).toLeft(())

  /** Chains multiple validators. */
  def chain[U <: T](other: Validator[U]): Validator[U] = {
    other match {
      case Validator.succeed => this
      case _                 => { in =>
        Error.ValidationError.combineOpt(this.validate(in), other.validate(in))
      }
    }
  }

  /** Contramaps */
  def contraMap[U](f: U => T): Validator[U] = { in =>
    validate(f(in))
  }
}

object Validator {

  case object succeed extends Validator[Any] {
    override def validate(value: Any): Option[ValidationError] = None

    override def chain[U <: Any](other: Validator[U]): Validator[U] = other
  }

  def fromPredicate[T](f: T => Boolean, msg: String): Validator[T] = input => {
    if (f(input)) {
      None
    } else {
      Some(Error.SingleValidationError(msg))
    }
  }

  def fromFunction[T](f: T => Option[ValidationError]): Validator[T] = (value: T) => f(value)

  def minLength(length: Int): Validator[String] =
    fromPredicate(_.length >= length, s"Length must be at least ${length} characters")

  def email: Validator[String] = fromPredicate(s => emailRegex.matches(s), "Not a valid Email address")

  private val emailRegex =
    """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r
}
