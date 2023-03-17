package kreuzberg.rpc

import kreuzberg.{AssemblyState, Provider}
import kreuzberg.util.Stateful
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
    override def provide: Stateful[AssemblyState, ZioApiRestClient] = {
      val url = "/api/"
      Stateful { current =>
        current.rootService("apirestclient", () => new ZioApiRestClient(url))
      }
    }
  }
}
