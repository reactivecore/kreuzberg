package kreuzberg.rpc

/**
 * A Special class holding a capability which the caller provides directly but the Callee has too proof (e.g.
 * Permissions). The proof is done during decoding.
 */
class ApiCapability[T](val value: T, checkValue: Option[Any] = None) {

  /** The Server side can request the result of the check. */
  def check(using c: ApiCheck[T]): c.Result = {
    checkValue
      .getOrElse {
        throw new IllegalStateException(s"Check not executed")
      }
      .asInstanceOf[c.Result]
  }
}

trait ApiCheck[T] {
  type Result <: Any

  /** Build a checked value */
  def checkAndBuild(value: T): ApiCapability[T] = ApiCapability(value, Some(check(value)))

  /**
   * Proof that the Api Call is valid (on server side).
   */
  @throws[Failure]
  def check(value: T): Result
}

object ApiCapability {

  /** The callers capability (unproven) */
  given capability[T](using value: T): ApiCapability[T] = ApiCapability(value, None)

  given encoder[T](using e: ParamEncoder[T]): ParamEncoder[ApiCapability[T]] with {
    override def encode(name: String, value: ApiCapability[T], request: Request): Request = {
      e.encode(name, value.value, request)
    }
  }

  given decoder[T](using d: ParamDecoder[T], check: ApiCheck[T]): ParamDecoder[ApiCapability[T]] with {
    override def decode(name: String, request: Request): ApiCapability[T] = {
      val value      = d.decode(name, request)
      val checkValue = check.check(value)
      ApiCapability(value, Some(checkValue))
    }
  }
}
