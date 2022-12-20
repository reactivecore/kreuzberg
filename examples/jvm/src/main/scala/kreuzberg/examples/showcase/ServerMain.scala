package kreuzberg.examples.showcase

import kreuzberg.miniserver.*
import kreuzberg.rpc.Dispatcher
import ZioEffect._

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
        api = Seq(
          Dispatcher.makeCustomDispatcher[zio.Task, String, Lister[zio.Task]](new ListerImpl)
        )
      )
    )
