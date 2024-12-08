package kreuzberg.extras.forms

import kreuzberg.extras.forms.Error.DecodingError

import scala.util.control.NonFatal

/** Lifts a value from an encoding */
trait Codec[U, T] {

  /** Encodes a value into a string. */
  def encode(value: U): T

  /** Decodes the value, either an error or the decoded value. */
  def decode(encoded: T): DecodingResult[U]

  /** Decode a value or throw. */
  @throws[UnhandledDecodingError]
  final def decodeOrThrow(transport: T): U = decode(transport) match {
    case Left(failure) => throw UnhandledDecodingError(failure)
    case Right(value)  => value
  }

  /** Maps the encoded type to another type. */
  def xmap[V](mapFn: U => V, contraMapFn: V => U): Codec[V, T] = Codec.Xmap(this, mapFn, contraMapFn)
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

  given simpleString: Codec[String, String] with {
    override def encode(value: String): String = value

    override def decode(encoded: String): DecodingResult[String] = Right(encoded)
  }

  given simpleInt: Codec[Int, String] with {
    override def encode(value: Int): String = value.toString

    override def decode(encoded: String): DecodingResult[Int] = {
      encoded.toIntOption.toRight(Error.DecodingError("Invalid Integer"))
    }
  }

  given simpleBoolean: Codec[Boolean, String] with {
    override def encode(value: Boolean): String = value.toString

    override def decode(encoded: String): DecodingResult[Boolean] = encoded.toBooleanOption.toRight(
      Error.DecodingError("Invalid Boolean")
    )
  }
}
