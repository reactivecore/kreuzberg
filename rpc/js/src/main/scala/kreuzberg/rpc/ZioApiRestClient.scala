package kreuzberg.rpc

import zio.{Task, ZIO}

class ZioApiRestClient(baseUrl: String) extends CallingBackend[Task, String] {
  override def call(service: String, name: String, input: String): Task[String] = {
    ZIO.fromFuture { ec =>
      new ApiRestClient(baseUrl)(ec).call(service, name, input)
    }
  }
}
