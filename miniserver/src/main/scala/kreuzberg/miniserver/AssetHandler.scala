package kreuzberg.miniserver

import AssetHandler.AssetEndpoint
import sttp.model.{MediaType, StatusCode}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.extension.MimeTypes
import sttp.tapir.server.ServerEndpoint

import java.io.InputStream
import java.nio.ByteBuffer
import scala.util.Using
import quest.*

object AssetHandler {
  case class AssetRequest(
      path: List[String],
      acceptEncoding: Option[String]
  ) {
    def acceptsBrotli: Boolean = acceptEncoding.exists(_.contains("br"))
  }

  case class AssetResponse(
      bytes: ByteBuffer,
      contentType: String,
      contentEncoding: Option[String] = None
  )

  object AssetResponse {
    def build(inputStream: InputStream, contentType: String, contentEncoding: Option[String]): AssetResponse = {
      Using.resource(inputStream) { _ =>
        AssetResponse(
          bytes = ByteBuffer.wrap(inputStream.readAllBytes()),
          contentType = contentType,
          contentEncoding = contentEncoding
        )
      }
    }
  }

  case class AssetFailure(
      statusCode: StatusCode
  )
  type AssetEndpoint = PublicEndpoint[AssetRequest, AssetFailure, AssetResponse, Any]

}

case class AssetHandler(prefix: Option[String], assetPaths: AssetPaths, deploymetConfig: DeploymentConfig) {

  import AssetHandler.*

  val assetEndpoint: AssetEndpoint = {
    val start = prefix
      .map { prefix =>
        endpoint.get.in(prefix / paths)
      }
      .getOrElse(
        endpoint.get.in(paths)
      )

    start
      .in(header[Option[String]]("Accept-Encoding"))
      .mapInTo[AssetRequest]
      .errorOut(statusCode)
      .out(byteBufferBody)
      .out(header[String]("Content-Type"))
      .out(header[Option[String]]("Content-Encoding"))
      .mapErrorOutTo[AssetFailure]
      .mapOutTo[AssetResponse]
  }

  val assetHandler: ServerEndpoint.Full[
    Unit,
    Unit,
    AssetRequest,
    AssetFailure,
    AssetResponse,
    Any,
    Identity
  ] = assetEndpoint.handle { request =>
    quest[Either[AssetFailure, AssetResponse]] {
      val fullName = request.path.mkString("/")
      if (deploymetConfig.isBlacklisted(fullName)) {
        bail(Left(AssetFailure(StatusCode.NotFound)))
      }

      val location = assetPaths.locateAsset(fullName, Some(deploymetConfig.deploymentType)).getOrElse {
        bail(Left(AssetFailure(StatusCode.NotFound)))
      }

      val contentType = MimeTypes
        .contentTypeByFileName(fullName)
        .getOrElse(
          MediaType.ApplicationOctetStream
        )
        .toString()

      if (request.acceptsBrotli) {
        val maybeBrotli = location.brotli()
        maybeBrotli.foreach { inputStream =>
          bail(Right(AssetResponse.build(inputStream, contentType, Some("br"))))
        }
      }

      Right(AssetResponse.build(location.load(), contentType = contentType, contentEncoding = None))
    }
  }
}
