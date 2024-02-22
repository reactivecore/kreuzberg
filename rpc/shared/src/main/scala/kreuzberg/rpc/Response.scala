package kreuzberg.rpc

import io.circe.{Decoder, Encoder, Json}

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
    statusCode: Int = 200
)

object Response {

  def fromJson(json: Json): Response = {
    val statusCode = json.as[StatusCodeExtractor].toOption.map(_.statusCode).getOrElse(200)
    Response(json, statusCode)
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
  def build[T](in: T)(using e: Encoder[T]): Response = {
    val json = e.apply(in)
    fromJson(json)
  }
}
