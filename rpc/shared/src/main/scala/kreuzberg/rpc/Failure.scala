package kreuzberg.rpc
import io.circe.{Codec, DecodingFailure, Encoder, Json, ParsingFailure}

import scala.util.control.NonFatal

sealed abstract class Failure(message: String, cause: Throwable = null) extends RuntimeException(message, cause) {
  def encode: EncodedError

  def encodeToJson: Json = encode.toJson
}

object Failure {
  def decodeFromJson(json: Json): Failure = {
    json.as[EncodedError] match {
      case Left(error) => Failure.fromDecodingFailure(error)
      case Right(ok)   => ok.decode
    }
  }

  def maybeFromJson(json: Json): Option[Failure] = {
    json.as[EncodedError] match {
      case Left(_)   => None
      case Right(ok) => Some(ok.decode)
    }
  }

  def decodeFromPlainJson(json: String): Failure = {
    io.circe.parser.parse(json) match {
      case Left(error) => Failure.fromParsingFailure(error)
      case Right(ok)   => decodeFromJson(ok)
    }
  }

  def fromCirceError(error: io.circe.Error): CodecError = {
    error match
      case p: ParsingFailure  => fromParsingFailure(p)
      case d: DecodingFailure => fromDecodingFailure(d)
  }

  def fromDecodingFailure(decodingFailure: DecodingFailure): CodecError = {
    CodecError("Could not decode", decodingFailure)
  }

  def fromParsingFailure(parsingFailure: ParsingFailure): CodecError = {
    CodecError("Could not parse", parsingFailure)
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

case class SecurityError(message: String, cause: Throwable = null) extends Failure(message, cause) {
  override def encode: EncodedError = EncodedError("SecurityError", message)
}

/** JSON Encoded error. */
case class EncodedError(
    _error: String,
    message: String,
    extra: Option[String] = None,
    code: Option[Int] = None
) derives Codec.AsObject {
  def toJson: Json = Encoder[EncodedError].apply(this)

  def decode: Failure = {
    _error match {
      case "CodecError"       => CodecError(message)
      case "UnknownService"   => UnknownServiceError(message)
      case "UnknownCall"      => UnknownCallError(extra.getOrElse(""), message)
      case "ServiceExecution" => ServiceExecutionError(message, code)
      case "ValidationFailed" => ValidationFailed(message)
      case "NotFound"         => NotFound(message)
      case "SecurityError"    => SecurityError(message)
      case other              => ServiceExecutionError(message, code)
    }
  }
}
