package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.TodoApi
import kreuzberg.miniserver.*
import kreuzberg.rpc.{Dispatcher, Failure, SecurityError}
import zio.ZIO

import scala.annotation.experimental

@experimental
object ServerMain
    extends Bootstrapper(
      MiniServerConfig(
        AssetPaths(
          Seq(
            AssetCandidatePath("examples/js/target/client_bundle/client/fast"),
            AssetCandidatePath("../examples/js/target/client_bundle/client/fast"),
            AssetCandidatePath("../../examples/js/target/client_bundle/client/fast")
          )
        ),
        api = Some(ZIO.attempt {
          Dispatcher.makeZioDispatcher[TodoApi[zio.Task]](new TodoService).preRequestFlatMap { request =>
            // Demonstrating adding a pre filter
            // Note: Headers are lower cased
            val id = request.headers.collectFirst {
              case (key, value) if key == "x-client-id" => value
            }
            id match {
              case None => ZIO.fail(SecurityError("Missing client id"))
              case _    => ZIO.succeed(request)
            }
          }
        })
      )
    )
