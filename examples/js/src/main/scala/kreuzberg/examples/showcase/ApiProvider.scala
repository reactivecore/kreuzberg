package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.{Api, TodoApi}
import kreuzberg.rpc.{ApiRestClient, Stub}

import scala.annotation.experimental
import scala.concurrent.Future

object ApiProvider {
  @experimental
  def create(): Api = {
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    val backend               = new ApiRestClient("/api/")
    val stub: TodoApi[Future] = Stub.makeStub[TodoApi[Future]](backend)
    Api(
      backend,
      stub
    )
  }
}
