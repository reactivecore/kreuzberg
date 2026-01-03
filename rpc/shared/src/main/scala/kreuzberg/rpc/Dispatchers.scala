package kreuzberg.rpc

/** Combines multiple dispatchers into one. */
case class Dispatchers[F[_]](
    dispatchers: Seq[Dispatcher[F]]
)(using effect: EffectSupport[F])
    extends Dispatcher[F] {

  def handles(serviceName: String): Boolean = {
    dispatchers.exists(_.handles(serviceName))
  }

  def call(serviceName: String, name: String, input: Request): F[Response] = {
    dispatchers.find(_.handles(serviceName)) match {
      case None    => effect.failure(UnknownServiceError(serviceName))
      case Some(d) => d.call(serviceName, name, input)
    }
  }

}
