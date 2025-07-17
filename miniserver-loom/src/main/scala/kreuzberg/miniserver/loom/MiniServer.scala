package kreuzberg.miniserver.loom

import kreuzberg.miniserver.{DeploymentType, Index, InitRequest, MiniServerConfig}
import org.slf4j.{Logger, LoggerFactory}
import ox.Ox
import sttp.capabilities.WebSockets
import sttp.model.headers.Cookie
import sttp.model.{Header, Headers, MediaType, StatusCode}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyConfig, NettySocketConfig}
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerBinding, OxStreams}
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.OnDecodeFailure.*
import MiniServer.*

/** Tapir/Netty Based small HTTP Server */
class MiniServer(config: MiniServerConfig[Identity], extraEndpoints: List[MiniServerEndpoint] = Nil) {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  /** Run and block forever. */
  def run(): Unit = {
    import ox.*
    // Also see call of NettySyncServer.startAndWait(..)
    supervised {
      useInScope(start())(_.stop()).discard
      never
    }
  }

  /** Just start the server. */
  def start(fastShutdown: Boolean = false)(using Ox): NettySyncServerBinding = {

    val socketConfig = NettySocketConfig.default.withReuseAddress

    val maybeFastshutdownNettyConfig: NettyConfig => NettyConfig = if (fastShutdown) { cfg =>
      cfg.noGracefulShutdown.withDontShutdownEventLoopGroupOnClose // hacky
    } else {
      identity
    }

    logger.info(s"Will start on port ${config.port} (mode=${config.deployment.deploymentType})")
    val server = NettySyncServer()
      .host(config.host)
      .port(config.port)
      .modifyConfig(_.socketConfig(socketConfig))
      .modifyConfig(maybeFastshutdownNettyConfig)
      .addEndpoints(endpoints)

    server.start()
  }

  /** All Endpoints. */
  protected def endpoints: List[MiniServerEndpoint] = primaryEndpoints ++ extraEndpoints ++ docEndpoints ++ secondaryEndpoints

  /** All Standard Endpoints. */
  protected def primaryEndpoints: List[MiniServerEndpoint] = List(
    assetHandler,
    indexHandler
  ) ++ apiEndpointHandler.toList

  /** Documentation Endpoints. */
  def docEndpoints: List[MiniServerEndpoint] = if (config.deployment.deploymentType == DeploymentType.Debug) {
    SwaggerInterpreter()
      .fromServerEndpoints[Identity](primaryEndpoints ++ extraEndpoints, "MiniServer", "0.1")
  } else {
    Nil
  }

  /** Endpoints which may have some kind of catch-all semantics. */
  protected def secondaryEndpoints: List[MiniServerEndpoint] = List(
    otherIndexHandler,
    rootAssetHandler
  )

  /** Handler for Assets inside assets-Path */
  val assetHandler =
    AssetHandler(Some("assets"), config.deployment.assetPaths, config.deployment).assetHandler

  /** Handler for Assets inside root Path */
  val rootAssetHandler = AssetHandler(None, config.deployment.rootAssets, config.deployment).assetHandler

  /** Endpoint for Root Page */
  val indexEndpoint: PublicEndpoint[(List[Header], List[Cookie]), Unit, String, Any] = {
    endpoint.get
      .in("")
      .in(headers)
      .in(cookies)
      .out(htmlBodyUtf8)
      .out(header(Header.contentType(MediaType.TextHtml)))
  }

  /** Handler For Index */
  val indexHandler = indexEndpoint.handleSuccess { case (headers, cookies) =>
    makeIndexHtml(headers, cookies)
  }

  /** Endpoint for index page from other locations (for reloading). */
  val otherIndexEndpoint: PublicEndpoint[(List[String], List[Header], List[Cookie]), StatusCode, String, Any] = {
    endpoint.get
      .in(
        paths
          .validate(Validator.custom[List[String]] { paths =>
            ValidationResult.validWhen(paths.lastOption.exists { elem =>
              val suffix = elem.indexOf('.') match {
                case -1 => None
                case n  => Some(elem.drop(n + 1))
              }
              suffix.forall(_ == "html")
            })
          })
          .onDecodeFailureNextEndpoint
      )
      .in(headers)
      .in(cookies)
      .errorOut(statusCode)
      .out(htmlBodyUtf8)
      .out(header(Header.contentType(MediaType.TextHtml)))
  }

  /** Handler for index page from other locations. */
  val otherIndexHandler = otherIndexEndpoint.handle { case (_, headers, cookies) =>
    Right(makeIndexHtml(headers, cookies))
  }

  /** Generate the Index HTML Page. */
  def makeIndexHtml(headers: List[Header], cookies: List[Cookie]): String = {
    val initRequest = InitRequest(
      headers = headers.map(h => h.name -> h.value),
      cookies = cookies.map(c => c.name -> c.value)
    )
    val initData    = config.init.map(_.apply(initRequest))
    Index(config.deployment).pageHtml(initData)
  }

  /** Endpoint handler for API-Calls. */
  val apiEndpointHandler = config.api.map(ApiHandler(_).handler)
}

object MiniServer {
  type MiniServerEndpoint = ServerEndpoint[OxStreams & WebSockets, Identity]
}
