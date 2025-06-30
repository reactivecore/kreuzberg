package kreuzberg.rpc

import io.circe.Encoder

/**
 * A Special return result, which modifies connection state, e.g. set cookies. The client doesn't see that.
 */
case class Mutation[+T](
    payload: T,
    setCookies: List[String] = Nil
)

object Mutation {
  given responseEncoder[T](using underlying: ResponseEncoder[T]): ResponseEncoder[Mutation[T]] with {
    override def build(value: Mutation[T]): Response = {
      val base = underlying.build(value.payload)
      base.copy(
        setCookies = base.setCookies ++ value.setCookies
      )
    }
  }

  given responseDecoder[T](using underlying: ResponseDecoder[T]): ResponseDecoder[Mutation[T]] with {
    override def decode(response: Response): Either[CodecError, Mutation[T]] = {
      underlying.decode(response).map(x => Mutation(x))
    }
  }
}
