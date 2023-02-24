package kreuzberg.examples.showcase

import kreuzberg.miniserver.{AssetCandidatePath, AssetPaths, Bootstrapper, MiniServerConfig, ZioEffect}
import kreuzberg.rpc.Dispatcher
import ZioEffect.*

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
        api = Seq(
          Dispatcher.makeCustomDispatcher[zio.Task, String, Lister[zio.Task]](new ListerImpl)
        )
      )
    )
