package kreuzberg.rpc

import scala.concurrent.{ExecutionContext, Future}

/** Interface for a calling out backend. */
trait CallingBackend[F[_]] {
  def call(service: String, name: String, input: Request): F[Response]

  def flatMapRequest(f: Request => F[Request])(implicit effectSupport: EffectSupport[F]): CallingBackend[F] = {
    val outer = this
    new CallingBackend[F] {
      override def call(service: String, name: String, input: Request): F[Response] = {
        effectSupport.flatMap(f(input)) { request =>
          outer.call(service, name, input)
        }
      }
    }
  }

  def mapRequest(f: Request => Request): CallingBackend[F] = {
    val outer = this
    new CallingBackend[F] {
      override def call(service: String, name: String, input: Request): F[Response] = {
        outer.call(service, name, f(input))
      }
    }
  }
}
