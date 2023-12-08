package kreuzberg.extras.forms

import kreuzberg.extras.forms.Error._

/** Encodes Errors of the Form system. */
sealed trait Error {
  def asList: List[String]

  def nest(path: String): Error
}

/** Either an Error or a value. */
type Result[T] = Either[Error, T]

/** Either an Decoding error or a value. */
type DecodingResult[T] = Either[DecodingError, T]

object Error {

  sealed trait ValidationError extends Error {

    override def nest(path: String): ValidationError
  }

  object ValidationError {
    def combineOpt(left: Option[ValidationError], right: Option[ValidationError]): Option[ValidationError] = {
      Seq(left, right).flatten.reduceLeftOption(combine)
    }

    def combine(a: ValidationError, b: ValidationError): ValidationError = {
      (a, b) match {
        case (MultipleValidationError(ae), MultipleValidationError(be)) =>
          MultipleValidationError(ae ++ be)
        case (MultipleValidationError(ae), _)                           =>
          MultipleValidationError(ae :+ b)
        case (_, MultipleValidationError(be))                           =>
          MultipleValidationError(a +: be)
        case (_, _)                                                     =>
          MultipleValidationError(List(a, b))
      }
    }
  }

  case class SingleValidationError(msg: String, path: List[String] = Nil) extends ValidationError {
    override def asList: List[String] = List(msg)

    override def nest(path: String): ValidationError = {
      copy(
        path = path :: this.path
      )
    }
  }

  case class MultipleValidationError(errors: List[ValidationError]) extends ValidationError {
    override def asList: List[String] = errors.flatMap(_.asList)

    override def nest(path: String): ValidationError = MultipleValidationError(errors.map(_.nest(path)))
  }

  case class DecodingError(msg: String, path: List[String] = Nil) extends Error {
    override def asList: List[String] = List(msg)

    override def nest(path: String): Error = {
      copy(
        path = path :: this.path
      )
    }
  }

  /** Builder for concatenating validation errors. */
  class ValidationErrorBuilder {
    private var current: Option[ValidationError] = None

    def add(error: Option[ValidationError], nest: String): Unit = {
      error.foreach { v =>
        val nested = v.nest(nest)
        current match {
          case Some(existing) => current = Some(ValidationError.combine(existing, nested))
          case None           => current = Some(nested)
        }
      }
    }

    def result: Option[ValidationError] = current
  }
}
