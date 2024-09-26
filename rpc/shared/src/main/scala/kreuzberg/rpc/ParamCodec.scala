package kreuzberg.rpc

import io.circe.{Decoder, Encoder}

/** Type class responsible for encoding / decoding a single parameter from a Request. */
trait ParamCodec[T] {
  def encode(name: String, value: T, request: Request): Request

  @throws[CodecError]
  def decode(name: String, request: Request): T
}

object ParamCodec {

  def apply[T](using p: ParamCodec[T]): ParamCodec[T] = p

  /** Default Param Codec for JSON Serializable objects. */
  given [T](using e: Encoder[T], d: Decoder[T]): ParamCodec[T] = new ParamCodec[T] {
    override def encode(name: String, value: T, request: Request): Request = {
      request.copy(
        payload = request.payload.add(name, e.apply(value))
      )
    }

    override def decode(name: String, request: Request): T = {
      request.payload(name) match {
        case Some(json) => d.decodeJson(json).toTry.get
        case None => throw new CodecError(s"Missing field ${name}")
      }
    }
  }
}
