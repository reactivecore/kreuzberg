package kreuzberg.miniserver
import kreuzberg.*
import kreuzberg.rpc.Dispatcher

/**
 * Configuration for MiniServer.
 * @tparam F
 *   Effect Type, can be Id.
 */
case class MiniServerConfig[F[_]](
    deployment: DeploymentConfig,
    host: String = "0.0.0.0",
    port: Int = 8090,
    api: Option[F[Dispatcher[F]]] = None,
    init: Option[InitRequest => F[String]] = None
)

case class InitRequest(
    headers: Seq[(String, String)],
    cookies: Seq[(String, String)]
)
