package kreuzberg.miniserver.loom

import kreuzberg.miniserver.{DeploymentType, Index, MiniServerConfig}
import org.slf4j.LoggerFactory
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettySocketConfig
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import java.nio.ByteBuffer
import scala.util.Using

class MiniServer(config: MiniServerConfig[Identity]) {
  val logger = LoggerFactory.getLogger(getClass)

  def run(): Unit = {

    val endpoints = List(
      assetHandler,
      indexHandler
    ) ++ apiEndpointHandler.toList

    val docEndpoints: List[ServerEndpoint[Any, Identity]] = SwaggerInterpreter()
      .fromServerEndpoints[Identity](endpoints, "MiniServer", "0.1")

    val socketConfig = NettySocketConfig.default.withReuseAddress

    logger.info(s"Will start on port ${config.port} (mode=${config.deployment.deploymentType})")
    NettySyncServer()
      .host(config.host)
      .port(config.port)
      .modifyConfig(_.socketConfig(socketConfig))
      .addEndpoints(endpoints)
      .addEndpoints(if (config.deployment.deploymentType == DeploymentType.Debug) docEndpoints else Nil)
      .addEndpoint(otherIndexHandler)
      .startAndWait()
  }

  private val indexHtml: String = Index(config.deployment).index.toString

  val assetEndpoint: PublicEndpoint[List[String], StatusCode, ByteBuffer, Any] = {
    endpoint.get
      .in("assets" / paths)
      .errorOut(statusCode)
      .out(byteBufferBody)
  }

  private val assetHandler = assetEndpoint.serverLogic[Identity] { paths =>
    val fullName = paths.mkString("/")
    config.deployment.locateAsset(fullName) match {
      case None        => Left(StatusCode.NotFound)
      case Some(value) =>
        Using.resource(value.load()) { data =>
          val bytes = data.readAllBytes()
          Right(ByteBuffer.wrap(bytes))
        }
    }
  }

  val indexEndpoint: PublicEndpoint[Unit, Unit, String, Any] = {
    endpoint.get
      .in("")
      .out(htmlBodyUtf8)
  }

  private val indexHandler = indexEndpoint.serverLogicSuccess[Identity] { _ =>
    indexHtml
  }

  val otherIndexEndpoint: PublicEndpoint[List[String], Unit, String, Any] = {
    endpoint.get
      .in(paths)
      .out(htmlBodyUtf8)
  }

  private val otherIndexHandler = otherIndexEndpoint.serverLogicSuccess[Identity] { _ =>
    indexHtml
  }

  val apiEndpointHandler = config.api.map(ApiHandler(_).handler)
}
