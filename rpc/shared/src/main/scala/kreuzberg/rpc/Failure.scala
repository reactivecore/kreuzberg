package kreuzberg.rpc
import upickle.default.*
import scala.util.control.NonFatal

sealed abstract class Failure(message: String, cause: Throwable = null) extends RuntimeException(message, cause) {
  def encode: EncodedError

  def encodeToJson: String = encode.toJson
}

object Failure {
  def decodeFromJson(json: String): Failure = {
    try {
      read[EncodedError](json).decode
    } catch {
      case NonFatal(e) => CodecError("Could not decode error", e)
    }
  }

  def fromThrowable(t: Throwable): Failure = {
    t match {
      case f: Failure => f
      case other      => ServiceExecutionError(other.getMessage())
    }
  }
}

case class CodecError(message: String, cause: Throwable = null) extends Failure(message, cause) {
  def encode: EncodedError = EncodedError("CodecError", message)
}

case class UnknownServiceError(serviceName: String) extends Failure("Unknown service " + serviceName) {
  def encode: EncodedError = EncodedError("UnknownService", serviceName)
}

case class UnknownCallError(serviceName: String, call: String)
    extends Failure("Unknown call " + call + s" in service ${serviceName}") {
  def encode: EncodedError = EncodedError("UnknownCall", call, Some(serviceName))
}

/** Generic execution error on server side. */
case class ServiceExecutionError(message: String, code: Option[Int] = None, cause: Throwable = null)
    extends Failure(message, cause) {
  def encode: EncodedError = EncodedError("ServiceExecution", message, None, code)
}

/** Some validation failed" */
case class ValidationFailed(message: String, cause: Throwable = null) extends Failure(message, cause) {
  override def encode: EncodedError = EncodedError("ValidationFailed", message)
}

/** Some resource was not found. */
case class NotFound(message: String, cause: Throwable = null) extends Failure(message, cause) {
  override def encode: EncodedError = EncodedError("NotFound", message)
}

/** JSON Encoded error. */
case class EncodedError(
    name: String,
    message: String,
    extra: Option[String] = None,
    code: Option[Int] = None
) {
  def toJson: String = write(this)

  def decode: Failure = {
    name match {
      case "CodecError"       => CodecError(message)
      case "UnknownService"   => UnknownServiceError(message)
      case "UnknownCall"      => UnknownCallError(extra.getOrElse(""), message)
      case "ServiceExecution" => ServiceExecutionError(message, code)
      case "ValidationFailed" => ValidationFailed(message)
      case "NotFound"         => NotFound(message)
      case other              => ServiceExecutionError(message, code)
    }
  }
}

object EncodedError {
  given writer: ReadWriter[EncodedError] = macroRW
}
