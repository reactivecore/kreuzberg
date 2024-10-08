package kreuzberg.rpc

import io.circe.{Decoder, Encoder}

/** Type class responsible for encoding a single parameter from a Request. */
trait ParamEncoder[T] {
  def encode(name: String, value: T, request: Request): Request
}

object ParamEncoder {
  def apply[T](using p: ParamEncoder[T]): ParamEncoder[T] = p

  given [T](using e: Encoder[T]): ParamEncoder[T] = new ParamEncoder[T] {
    override def encode(name: String, value: T, request: Request): Request = {
      request.copy(
        payload = request.payload.add(name, e.apply(value))
      )
    }
  }
}

/** Type class responsible for decoding a single parameter from a Request */
trait ParamDecoder[T] {
  @throws[CodecError]
  def decode(name: String, request: Request): T
}

object ParamDecoder {

  def apply[T](using p: ParamDecoder[T]): ParamDecoder[T] = p

  /** Default Param Codec for JSON Serializable objects. */
  given [T](using d: Decoder[T]): ParamDecoder[T] = new ParamDecoder[T] {
    override def decode(name: String, request: Request): T = {
      request.payload(name) match {
        case Some(json) => d.decodeJson(json).toTry.get
        case None       => throw new CodecError(s"Missing field ${name}")
      }
    }
  }
}
