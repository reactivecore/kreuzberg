package kreuzberg.rpc

import kreuzberg.{Provider, ServiceRepository}
import zio.{Task, ZIO}

import scala.concurrent.Future

class ZioApiRestClient(baseUrl: String) extends CallingBackend[Task, String] {
  override def call(service: String, name: String, input: String): Task[String] = {
    ZIO.fromFuture { ec =>
      new ApiRestClient(baseUrl)(ec).call(service, name, input)
    }
  }
}

object ZioApiRestClient {
  given provider: Provider[CallingBackend[Task, String]] with {

    override def name: String = "apirestclient"

    override def create(using serviceRepository: ServiceRepository): CallingBackend[Task, String] = {
      val url = "/api/"
      new ZioApiRestClient(url)
    }

  }
}
