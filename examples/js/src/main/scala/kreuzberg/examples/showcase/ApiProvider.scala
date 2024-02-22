package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.{Api, TodoApi}
import kreuzberg.rpc.{ApiRestClient, Stub}

import scala.annotation.experimental
import scala.concurrent.Future
import scala.util.Random

object ApiProvider {
  @experimental
  def create(): Api = {
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    // Note: not secure
    // Demonstrating wrapping the backend client
    // Note: Headers are lower case
    val id = Random().nextLong().toString

    val backend = new ApiRestClient("/api/")
      .mapRequest(_.withHeader("x-client-id", id))

    val stub: TodoApi[Future] = Stub.makeStub[TodoApi[Future]](backend)
    Api(
      backend,
      stub
    )
  }
}
