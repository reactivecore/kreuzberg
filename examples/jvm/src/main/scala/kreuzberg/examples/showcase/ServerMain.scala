package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.TodoApi
import kreuzberg.miniserver.*
import kreuzberg.rpc.Dispatcher
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
          Dispatcher.makeZioDispatcher[TodoApi[zio.Task]](new TodoService)
        })
      )
    )
