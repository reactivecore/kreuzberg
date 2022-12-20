package kreuzberg.rpc

import upickle.default.*

import scala.util.control.NonFatal

/** Encapsulates encoding of single values. */
trait Codec[U, T] {

  /** Encode a value. */
  def encode(value: U): T

  /** Decode a value. */
  def decode(transport: T): Either[CodecError, U]
}

object Codec {
  implicit def upickleCodec[U](implicit r: ReadWriter[U]): Codec[U, String] = new Codec[U, String] {
    override def encode(value: U): String = write(value)

    override def decode(transport: String): Either[CodecError, U] = try {
      Right(read(transport))
    } catch {
      case NonFatal(e) => Left(CodecError(e.getMessage))
    }
  }
}
