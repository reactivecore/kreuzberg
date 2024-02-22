package kreuzberg.rpc

import io.circe.Json

/** A serialized request which is transferred using a POST-Request. */
case class Request(
    payload: Json,
    headers: Seq[(String, String)] = Nil
) {
  def withHeader(name: String, value: String): Request = {
    copy(headers = headers :+ (name, value))
  }
}

object Request {
  def fromJsonString(json: String, headers: Seq[(String, String)]): Either[Failure, Request] = {
    io.circe.parser.parse(json) match {
      case Left(failure) => Left(Failure.fromParsingFailure(failure))
      case Right(ok)     => Right(Request(ok, headers))
    }
  }

  def forceJsonString(json: String, headers: Seq[(String, String)] = Nil): Request = {
    fromJsonString(json, headers).fold(f => throw (f), identity)
  }
}
