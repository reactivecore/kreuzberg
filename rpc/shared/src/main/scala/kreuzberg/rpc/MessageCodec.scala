package kreuzberg.rpc

import scala.util.control.NonFatal

/** Responsible for encoding/decoding multiple named arguments into one value. */
trait MessageCodec[T] {

  /**
   * Merge multiple attributes into one message.
   *
   * @param args
   *   mapping from argument name to value
   */
  def combine(args: (String, T)*): T

  /**
   * Splite multiple attributes from one message.
   *
   * @param combined
   *   combined message
   * @param argNames
   *   expected argument name
   */
  def split(combined: T, argNames: Seq[String]): Either[CodecError, Seq[T]]
}

object MessageCodec {
  implicit val jsonArrayCodec: MessageCodec[String] = new MessageCodec[String] {
    import upickle.default._
    override def combine(args: Seq[(String, String)]): String = {
      args
        .map { case (key, value) =>
          (write(key) + ":" + value)
        }
        .mkString("{", ",", "}")
    }

    override def split(combined: String, argNames: Seq[String]): Either[CodecError, Seq[String]] = {
      try {
        val asMap  = read[ujson.Obj](combined).value
        val fields = argNames.map { name =>
          asMap.getOrElse(name, throw new CodecError(s"Missing field ${name}"))
        }
        Right(fields.map(_.toString))
      } catch {
        case c: CodecError => throw c
        case NonFatal(e)   =>
          Left(CodecError(s"Could not decode ${combined}"))
      }
    }
  }
}
