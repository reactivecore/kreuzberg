package kreuzberg.rpc

/** Combines multiple dispatchers into one. */
case class Dispatchers[F[_], T](
    dispatchers: Seq[Dispatcher[F, T]]
)(using effect: Effect[F])
    extends Dispatcher[F, T] {

  def handles(serviceName: String): Boolean = {
    dispatchers.exists(_.handles(serviceName))
  }

  def call(serviceName: String, name: String, input: T): F[T] = {
    dispatchers.find(_.handles(serviceName)) match {
      case None    => effect.failure(UnknownServiceError(serviceName))
      case Some(d) => d.call(serviceName, name, input)
    }
  }

}
