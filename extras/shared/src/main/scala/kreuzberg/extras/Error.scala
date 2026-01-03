package kreuzberg.extras

import kreuzberg.extras.Error.*

/** Encodes Errors of the Form / URL system. */
sealed trait Error {

  /** Format as list of errors. */
  def asList: List[String]
}

/** Either an Error or a value. */
type Result[T] = Either[Error, T]

/** Either an Decoding error or a value. */
type DecodingResult[T] = Either[DecodingError, T]

object Error {
  sealed trait PathError extends Error {
    override def asList: List[String] = List(toString)
  }

  case object NestedPathError extends PathError {
    override def toString: String = "There is an unexpected nested path"
  }

  case object MissingNestedPathError extends PathError {
    override def toString: String = "There is a missing nested path"
  }

  case class BadPathError(got: UrlPath, expected: UrlPath) extends PathError {
    override def toString: String = s"Bad path, expected: ${expected}, got: ${got}"
  }

  case class BadPathElementError(got: String, expected: String) extends PathError {
    override def toString: String = s"Bad path element, expected: ${expected}, got: ${got}"
  }

  case class MissingQueryParameter(key: String) extends PathError {
    override def toString: String = s"Missing query parameter: ${key}"
  }

  case class PathNotFound(msg: String) extends PathError {
    override def toString: String = msg
  }

  case class PathCodecException(error: Error) extends Exception(error.toString, null, false, false) with Error {
    override def asList: List[String] = error.asList
  }

  /** An error which can have a nested position. */
  sealed trait PositionalError extends Error {
    def nest(path: String): PositionalError
  }

  sealed trait ValidationError extends PositionalError {
    def asList: List[String]

    override def nest(path: String): ValidationError

    override def toString: String = s"ValidationError: ${asList.mkString(",")}"
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

  case class DecodingError(msg: String, path: List[String] = Nil) extends PositionalError {
    override def asList: List[String] = List(msg)

    override def nest(path: String): DecodingError = {
      copy(
        path = path :: this.path
      )
    }
  }

  /** Builder for concatenating validation errors. */
  class ValidationErrorBuilder {
    private var current: Option[ValidationError] = None // scalafix:ok

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

  /** Captures other Throwables. */
  case class NestedThrowable(e: Throwable) extends Error {
    override def asList: List[String] = List(e.getMessage)
  }

  def fromThrowable(t: Throwable): Error = {
    t match {
      case pathCodecException: PathCodecException => pathCodecException.error
      case other                                  => NestedThrowable(other)
    }
  }
}
