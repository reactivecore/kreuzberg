package kreuzberg.rpc

import kreuzberg.{Logger, Provider, ServiceRepository}

import scala.concurrent.{ExecutionContext, Future}
import org.scalajs.dom.Fetch
import org.scalajs.dom.Request
import org.scalajs.dom.RequestInit
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.Headers

import scala.scalajs.js.Thenable.Implicits.*
import org.scalajs.dom.Response

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

object ApiRestClient {
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  given provider: Provider[CallingBackend[Future, String]] with {

    override def name: String = "apirestclient"

    override def create(using serviceRepository: ServiceRepository): CallingBackend[Future, String] = {
      val url = "/api/"
      new ApiRestClient(url)
    }
  }
}
