package kreuzberg.rpc

import scala.util.Try

trait MessageCodec[T] {

  def encode[A](value: A)(implicit c: Codec[A, T]): T = c.encode(value)

  def decode[A](value: T)(implicit c: Codec[A, T]): Either[CodecError, A] = c.decode(value)

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
      args.map(_._2).mkString("[", ",", "]")
    }

    override def split(combined: String, argNames: Seq[String]): Either[CodecError, Seq[String]] = {
      Try { read[ujson.Arr](combined).value }.toEither.left.map(x => CodecError(x.toString, x)).flatMap { array =>
        if (array.length < argNames.length) {
          Left(CodecError(s"Expected ${argNames.length}, got ${array.length}"))
        } else {
          Right(array.map(_.toString).toSeq)
        }
      }
    }
  }
}
