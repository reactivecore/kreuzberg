package kreuzberg.rpc

import io.circe.{Decoder, DecodingFailure, Json}

import scala.util.control.NonFatal

/** Code for encoding full messages in a JSON object. */
object MessageCodec {
  def combine(args: (String, Json)*): Json = {
    Json.obj(args: _*)
  }

  def split(from: Json, argNames: Seq[String]): Decoder.Result[Seq[Json]] = {
    import cats.implicits.*
    for {
      asObject <- from.as[Map[String, Json]]
      fields   <- argNames.map { argName =>
                    asObject.get(argName).toRight(DecodingFailure(s"Missing field ${argName}", Nil))
                  }.sequence
    } yield {
      fields
    }
  }
}
