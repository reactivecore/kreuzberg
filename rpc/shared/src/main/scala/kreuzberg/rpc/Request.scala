package kreuzberg.rpc

import io.circe.JsonObject

/** A serialized request which is transferred using a POST-Request. */
case class Request(
    payload: JsonObject,
    headers: Seq[(String, String)] = Nil,
    cookies: Seq[(String, String)] = Nil
) {
  def withHeader(name: String, value: String): Request = {
    copy(headers = headers :+ (name, value))
  }

  lazy val lowerCaseHeaderMap: Map[String, String] = headers.map { case (key, value) =>
    key.toLowerCase -> value
  }.toMap

  /**
   * Force decoding of a header. Note: you should use lower case header names
   */
  def forceHeaderDecode(name: String): String = {
    lowerCaseHeaderMap.getOrElse(name, throw CodecError(s"Missing header ${name}"))
  }
}

object Request {
  def empty: Request                                                                         = Request(JsonObject())
  def fromJsonString(json: String, headers: Seq[(String, String)]): Either[Failure, Request] = {
    io.circe.parser.decode[JsonObject](json) match {
      case Left(failure) => Left(Failure.fromCirceError(failure))
      case Right(ok)     => Right(Request(ok, headers))
    }
  }

  def forceJsonString(json: String, headers: Seq[(String, String)] = Nil): Request = {
    fromJsonString(json, headers).fold(f => throw (f), identity)
  }
}
