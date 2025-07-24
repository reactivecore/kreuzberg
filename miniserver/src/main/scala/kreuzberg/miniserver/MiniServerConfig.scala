package kreuzberg.miniserver

import kreuzberg.*
import kreuzberg.rpc.{Dispatcher, Id}

/**
 * Configuration for MiniServer.
 */
case class MiniServerConfig(
    deployment: DeploymentConfig,
    host: String = "0.0.0.0",
    port: Int = 8090,
    api: Option[Dispatcher[Id]] = None,
    init: Option[InitRequest => String] = None
)

case class InitRequest(
    headers: Seq[(String, String)],
    cookies: Seq[(String, String)]
)
