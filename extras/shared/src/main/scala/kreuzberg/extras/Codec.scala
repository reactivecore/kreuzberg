package kreuzberg.extras

import kreuzberg.extras
import kreuzberg.extras.DecodingResult
import kreuzberg.extras.Error.DecodingError

import java.util.UUID
import scala.util.Try

/**
 * Lifts a value from an encoding
 * @tparam A
 *   lifted value
 * @tparam T
 *   transport value.
 */
trait Codec[A, T] {

  /** Encodes a value into a string. */
  def encode(value: A): T

  /** Decodes the value, either an error or the decoded value. */
  def decode(encoded: T): DecodingResult[A]

  /** Decode a value or throw. */
  @throws[UnhandledDecodingError]
  final def decodeOrThrow(transport: T): A = decode(transport) match {
    case Left(failure) => throw UnhandledDecodingError(failure)
    case Right(value)  => value
  }

  /** Maps the encoded type to another type. */
  def xmap[V](mapFn: A => V, contraMapFn: V => A): Codec[V, T] = Codec.Xmap(this, mapFn, contraMapFn)
}

/** A not handled error in [[Codec.decodeOrThrow()]] */
case class UnhandledDecodingError(codecError: DecodingError)
    extends RuntimeException(codecError.asList.mkString(","), null, false, false)

object Codec {

  /** Construct from functions. */
  def fromEncoderAndDecoder[U, T](encoder: U => T, decoder: T => DecodingResult[U]): Codec[U, T] = new Codec[U, T] {
    override def encode(value: U): T = encoder(value)

    override def decode(encoded: T): DecodingResult[U] = decoder(encoded)
  }

  case class Xmap[A, B, E](underlying: Codec[A, E], mapFn: A => B, contraMapFn: B => A) extends Codec[B, E] {
    override def encode(value: B): E = underlying.encode(contraMapFn(value))

    override def decode(encoded: E): DecodingResult[B] = underlying.decode(encoded).map(mapFn)
  }

  type FromStringCodec[T] = Codec[T, String]

  abstract class SimpleFromStringCodec[T](name: String) extends FromStringCodec[T] {
    def decodeOpt(value: String): Option[T]

    override def decode(encoded: String): DecodingResult[T] = {
      decodeOpt(encoded).toRight(DecodingError(s"Invalid ${name}"))
    }
  }

  given identical[T]: Codec[T, T] with {
    override def encode(value: T): T = value

    override def decode(encoded: T): DecodingResult[T] = Right(encoded)
  }

  given string: Codec[String, String] = identical

  given simpleInt: SimpleFromStringCodec[Int]("Integer") with {

    override def encode(value: Int): String = value.toString

    override def decodeOpt(value: String): Option[Int] = value.toIntOption
  }

  given simpleLong: SimpleFromStringCodec[Long]("Long") with {
    override def encode(value: Long): String = value.toString

    override def decodeOpt(value: String): Option[Long] = value.toLongOption
  }

  given simpleBoolean: SimpleFromStringCodec[Boolean]("Boolean") with {
    override def encode(value: Boolean): String = value.toString

    override def decodeOpt(value: String): Option[Boolean] = value.toBooleanOption
  }

  given simpleUuid: SimpleFromStringCodec[UUID]("UUID") with {
    override def encode(value: UUID): String = value.toString

    override def decodeOpt(value: String): Option[UUID] = Try(UUID.fromString(value)).toOption
  }
}
