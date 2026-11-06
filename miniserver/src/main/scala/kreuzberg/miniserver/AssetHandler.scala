package kreuzberg.miniserver

import AssetHandler.AssetEndpoint
import sttp.model.{HeaderNames, MediaType, StatusCode}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.extension.MimeTypes
import sttp.tapir.server.ServerEndpoint

import java.io.InputStream
import java.nio.ByteBuffer
import scala.util.Using
import quest.*

/**
 * Serves assets.
 *
 * Every asset gets an ETag (SHA-256 of its content), a matching `If-None-Match` is answered with 304 Not Modified. In
 * production, requests whose `hash` query parameter matches the content (see [[AssetPaths.hashedUrl]]) may be cached
 * forever, all others must be revalidated.
 */
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
      .in(header[Option[String]](HeaderNames.AcceptEncoding))
      // A list, as there may be multiple headers
      .in(header[List[String]](HeaderNames.IfNoneMatch))
      .in(query[Option[String]]("hash"))
      .mapInTo[AssetRequest]
      .errorOut(statusCode)
      .mapErrorOutTo[AssetFailure]
      .out(
        oneOf[AssetResponse](
          oneOfVariant(StatusCode.Ok, fullOutput),
          oneOfVariant(StatusCode.NotModified, notModifiedOutput)
        )
      )
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

      val deploymentType = Some(deploymetConfig.deploymentType)
      val hash           = location.hashSha256(deploymentType)
      val brotli         = if (request.acceptsBrotli) location.brotliVariant() else None
      // The Brotli variant has other bytes, so its ETag is its own hash
      val served         = brotli.getOrElse(location)
      val etag           = s"\"${brotli.map(_.hashSha256(deploymentType)).getOrElse(hash)}\""
      val cacheControl   =
        if (deploymetConfig.deploymentType == DeploymentType.Production && request.hash.contains(hash)) {
          ImmutableCacheControl
        } else {
          RevalidateCacheControl
        }

      if (request.ifNoneMatch.exists(matchesETag(_, etag))) {
        // Without a Content-Length, Netty sends 0, but it must be the size of the full response
        bail(Right(AssetResponse.NotModified(served.size(), etag = etag, cacheControl = cacheControl)))
      }

      val contentType = MimeTypes
        .contentTypeByFileName(fullName)
        .getOrElse(
          MediaType.ApplicationOctetStream
        )
        .toString()

      val contentEncoding = brotli.map(_ => "br")
      Right(AssetResponse.Full.build(served.load(), contentType, contentEncoding, etag, cacheControl))
    }
  }
}

object AssetHandler {
  case class AssetRequest(
      path: List[String],
      acceptEncoding: Option[String],
      ifNoneMatch: List[String],
      hash: Option[String]
  ) {
    def acceptsBrotli: Boolean = acceptEncoding.exists(_.contains("br"))
  }

  sealed trait AssetResponse

  object AssetResponse {

    /** The asset itself (200). */
    case class Full(
        bytes: ByteBuffer,
        contentType: String,
        contentEncoding: Option[String],
        etag: String,
        cacheControl: String
    ) extends AssetResponse

    object Full {
      def build(
          inputStream: InputStream,
          contentType: String,
          contentEncoding: Option[String],
          etag: String,
          cacheControl: String
      ): Full = {
        Using.resource(inputStream) { _ =>
          Full(
            bytes = ByteBuffer.wrap(inputStream.readAllBytes()),
            contentType = contentType,
            contentEncoding = contentEncoding,
            etag = etag,
            cacheControl = cacheControl
          )
        }
      }
    }

    /** The client's copy is still valid (304). */
    case class NotModified(
        contentLength: Long,
        etag: String,
        cacheControl: String
    ) extends AssetResponse
  }

  case class AssetFailure(
      statusCode: StatusCode
  )

  type AssetEndpoint = PublicEndpoint[AssetRequest, AssetFailure, AssetResponse, Any]

  /** Cache-Control for assets requested with their current hash, they never change. */
  val ImmutableCacheControl: String = "public, max-age=31536000, immutable"

  /** Cache-Control for all other assets, the client may keep them but must ask (ETag) before using them. */
  val RevalidateCacheControl: String = "no-cache"

  /**
   * Returns true if an `If-None-Match` header matches the ETag. Uses weak comparison, as proxies (e.g. when
   * compressing) may turn the ETag into a weak one.
   */
  def matchesETag(ifNoneMatch: String, etag: String): Boolean = {
    ifNoneMatch.split(',').iterator.map(_.trim).exists { candidate =>
      candidate == "*" || candidate.stripPrefix("W/") == etag
    }
  }

  private val fullOutput: EndpointOutput[AssetResponse.Full] = byteBufferBody
    .and(header[String](HeaderNames.ContentType))
    .and(header[Option[String]](HeaderNames.ContentEncoding))
    .and(cachingOutput)
    .mapTo[AssetResponse.Full]

  private val notModifiedOutput: EndpointOutput[AssetResponse.NotModified] =
    header[Long](HeaderNames.ContentLength).and(cachingOutput).mapTo[AssetResponse.NotModified]

  private def cachingOutput = header[String](HeaderNames.Etag)
    .and(header[String](HeaderNames.CacheControl))
    .and(header(HeaderNames.Vary, HeaderNames.AcceptEncoding))
}
