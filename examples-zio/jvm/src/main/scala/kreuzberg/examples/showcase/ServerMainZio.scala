package kreuzberg.examples.showcase

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
            Dispatcher.makeCustomDispatcher[zio.Task, String, Lister[zio.Task]](new ListerImpl)
          )
        }
      )
    )
