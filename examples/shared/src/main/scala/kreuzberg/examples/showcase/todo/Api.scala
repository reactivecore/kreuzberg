package kreuzberg.examples.showcase.todo

import kreuzberg.ServiceNameProvider
import kreuzberg.rpc.CallingBackend

import scala.concurrent.Future

/** API, which gets injected as a service. */
case class Api(
    callingBackend: CallingBackend[Future],
    todoApi: TodoApi[Future]
)

object Api {
  given snp: ServiceNameProvider[Api] = ServiceNameProvider.create("api")
}
