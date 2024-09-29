package kreuzberg.rpc

import kreuzberg.rpc
import org.scalajs.dom.*

import scala.concurrent.{ExecutionContext, Future}

/** Rest client for API Calls. */
class ApiRestClient(baseUrl: String)(implicit ec: ExecutionContext) extends CallingBackend[Future] {

  override def call(service: String, name: String, input: rpc.Request): Future[rpc.Response] = {
    val url = baseUrl + service + "/" + name

    val headers = Headers()
    headers.append("Content-Type", "application/json")
    input.headers.foreach { case (k, v) =>
      headers.append(k, v)
    }

    object init extends RequestInit
    init.method = HttpMethod.POST
    init.body = input.payload.toJson.toString
    init.headers = headers

    for {
      fetch   <- Fetch.fetch(url, init).toFuture
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
