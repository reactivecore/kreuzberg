package kreuzberg.rpc

import io.circe.{Decoder, Encoder, Json}
import kreuzberg.rpc.Response.StatusCodeExtractor

/**
 * A Response from a dispatcher.
 *
 * @param json
 *   Serialized response
 * @param statusCode
 *   status code to use. By default this is taken from the JSON (see [[Response.fromJsonString()]])
 */
case class Response(
    json: Json,
    statusCode: Int = 200,
    setCookies: List[String] = Nil
)

/** Builds responses from arbitrary types. */
trait ResponseEncoder[T] {
  def build(value: T): Response
}

object ResponseEncoder {

  /** Derive from JSON Encoder. */
  given fromEncoder[T](using e: Encoder[T]): ResponseEncoder[T] with {
    override def build(value: T): Response = {
      val json       = e(value)
      val statusCode = json.as[StatusCodeExtractor].toOption.map(_.statusCode).getOrElse(200)
      Response(
        json,
        statusCode,
        Nil
      )
    }
  }
}

/** Decodes responses into arbitrary types. */
trait ResponseDecoder[T] {
  def decode(response: Response): Either[CodecError, T]
}

object ResponseDecoder {

  /** Derive from JSON Decoder. */
  given fromDecoder[T](using d: Decoder[T]): ResponseDecoder[T] with {
    override def decode(response: Response): Either[CodecError, T] = {
      d.decodeJson(response.json) match {
        case Left(bad) => Left(Failure.fromDecodingFailure(bad))
        case Right(ok) => Right(ok)
      }
    }
  }
}

object Response {

  def fromJson(json: Json): Response = {
    build(json)
  }

  def fromJsonString(json: String): Either[CodecError, Response] = {
    io.circe.parser.parse(json) match {
      case Left(failure) => Left(Failure.fromParsingFailure(failure))
      case Right(ok)     => Right(fromJson(ok))
    }
  }

  def forceJsonString(json: String): Response = {
    fromJsonString(json).fold(e => throw (e), identity)
  }

  case class StatusCodeExtractor(statusCode: Int) derives Decoder

  /** Build a response from a value. StatusCode will be set, if field `statusCode` is set. */
  def build[T](in: T)(using r: ResponseEncoder[T]): Response = {
    r.build(in)
  }
}
