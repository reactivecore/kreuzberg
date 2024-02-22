package kreuzberg.rpc

import zio.{Task, ZIO}

class ZioApiRestClient(baseUrl: String) extends CallingBackend[Task] {
  override def call(service: String, name: String, input: Request): Task[Response] = {
    ZIO.fromFuture { ec =>
      new ApiRestClient(baseUrl)(ec).call(service, name, input)
    }
  }
}
