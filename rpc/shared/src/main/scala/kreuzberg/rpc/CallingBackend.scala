package kreuzberg.rpc

import scala.concurrent.{ExecutionContext, Future}

/** Interface for a calling out backend. */
trait CallingBackend[F[_], T] {
  def call(service: String, name: String, input: T): F[T]
}
