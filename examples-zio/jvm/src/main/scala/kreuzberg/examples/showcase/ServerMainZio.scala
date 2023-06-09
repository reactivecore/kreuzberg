package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.TodoApi
import kreuzberg.miniserver.{AssetCandidatePath, AssetPaths, Bootstrapper, MiniServerConfig}
import kreuzberg.rpc.Dispatcher
import zio.ZIO

object ServerMainZio
    extends Bootstrapper(
      MiniServerConfig(
        AssetPaths(
          Seq(
            AssetCandidatePath("examples-zio/js/target/client_bundle/client/fast"),
            AssetCandidatePath("../examples-zio/js/target/client_bundle/client/fast"),
            AssetCandidatePath("../../examples-zio/js/target/client_bundle/client/fast")
          )
        ),
        api = Some {
          ZIO.attempt(
            Dispatcher.makeCustomDispatcher[zio.Task, String, TodoApi[zio.Task]](new TodoService)
          )
        }
      )
    )
