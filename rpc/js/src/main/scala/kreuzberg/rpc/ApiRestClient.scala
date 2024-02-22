package kreuzberg.rpc

import kreuzberg.{Logger, rpc}
import org.scalajs.dom.*

import scala.concurrent.{ExecutionContext, Future}

/** Rest client for API Calls. */
class ApiRestClient(baseUrl: String)(implicit ec: ExecutionContext) extends CallingBackend[Future] {

  override def call(service: ByteString, name: ByteString, input: rpc.Request): Future[rpc.Response] = {
    val url = baseUrl + service + "/" + name

    val headers = Headers()
    headers.append("Content-Type", "application/json")
    input.headers.foreach { case (k, v) =>
      headers.append(k, v)
    }

    object init extends RequestInit
    init.method = HttpMethod.POST
    init.body = input.payload.toString
    init.headers = headers

    val t0 = System.currentTimeMillis()
    Logger.debug(s"Calling ${url}")
    for {
      fetch   <- Fetch.fetch(url, init).toFuture
      t1       = System.currentTimeMillis()
      _        = Logger.debug(s"${url} responded with ${fetch.status} after ${t1 - t0}ms")
      content <- decodeResponse(fetch)
    } yield {
      content
    }
  }

  private def decodeResponse(response: Response): Future[rpc.Response] = {
    response.text().toFuture.flatMap { message =>
      // We always parse. RPC-Errors are detected by `_error`-Field
      io.circe.parser.parse(message) match {
        case Left(fail) => Future.failed(Failure.fromParsingFailure(fail))
        case Right(ok)  =>
          Failure.maybeFromJson(ok) match {
            case Some(failure) => Future.failed(failure)
            case None          => Future.successful(rpc.Response(ok, response.status))
          }
      }
    }
  }
}
