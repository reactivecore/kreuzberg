package kreuzberg.rpc

import kreuzberg.Logger
import org.scalajs.dom.*

import scala.concurrent.{ExecutionContext, Future}

/** Rest client for API Calls. */
class ApiRestClient(baseUrl: String)(implicit ec: ExecutionContext) extends CallingBackend[Future, String] {
  def call(service: String, name: String, input: String): Future[String] = {
    val url = baseUrl + service + "/" + name

    object init extends RequestInit
    init.method = HttpMethod.POST
    init.body = input
    init.headers = Headers().append("Content-Type", "application/json")

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

  private def decodeResponse(response: Response): Future[String] = {
    response.text().toFuture.flatMap { message =>
      if (response.status == 200) {
        Future.successful(message)
      } else {
        val decode = Failure.decodeFromJson(message)
        Future.failed(decode)
      }
    }
  }
}
