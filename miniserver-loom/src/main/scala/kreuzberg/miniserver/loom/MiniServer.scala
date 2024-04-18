package kreuzberg.miniserver.loom

import kreuzberg.miniserver.{DeploymentType, Index, MiniServerConfig}
import org.slf4j.LoggerFactory
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.loom.{Id, NettyIdServer}
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import java.nio.ByteBuffer
import scala.util.Using

class MiniServer(config: MiniServerConfig[Id]) {
  val logger = LoggerFactory.getLogger(getClass)

  def run(): Unit = {

    val endpoints = List(
      assetHandler,
      indexHandler
    ) ++ apiEndpointHandler.toList ++ List(
      otherIndexHandler
    )

    val docEndpoints: List[ServerEndpoint[Any, Id]] = SwaggerInterpreter()
      .fromServerEndpoints[Id](endpoints, "MiniServer", "0.1")

    val server = NettyIdServer()
      .port(config.port)
      .addEndpoints(endpoints)
      .addEndpoints(if (config.deploymentType.contains(DeploymentType.Debug)) docEndpoints else Nil)
      .start()
    logger.info(s"Listening on port ${config.port}")
  }

  private val indexHtml: String = Index(config).index.toString

  val assetEndpoint: PublicEndpoint[List[String], StatusCode, ByteBuffer, Any] = {
    endpoint.get
      .in("assets" / paths)
      .errorOut(statusCode)
      .out(byteBufferBody)
  }

  private val assetHandler = assetEndpoint.serverLogic[Id] { paths =>
    val fullName = paths.mkString("/")
    config.locateAsset(fullName) match {
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

  private val indexHandler = indexEndpoint.serverLogicSuccess[Id] { _ =>
    indexHtml
  }

  val otherIndexEndpoint: PublicEndpoint[List[String], Unit, String, Any] = {
    endpoint.get
      .in(paths)
      .out(htmlBodyUtf8)
  }

  private val otherIndexHandler = otherIndexEndpoint.serverLogicSuccess[Id] { _ =>
    indexHtml
  }

  val apiEndpointHandler = config.api.map(ApiHandler(_).handler)
}
