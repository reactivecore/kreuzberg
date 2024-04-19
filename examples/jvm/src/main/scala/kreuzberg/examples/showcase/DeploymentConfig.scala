package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.TodoApi
import kreuzberg.miniserver.{AssetCandidatePath, AssetPaths, DeploymentConfig, MiniServerConfig}
import kreuzberg.rpc.{Dispatcher, SecurityError}
import zio.ZIO

val deploymentConfig = DeploymentConfig(
  AssetPaths(
    Seq(
      AssetCandidatePath("examples/js/target/client_bundle/client/fast"),
      AssetCandidatePath("../examples/js/target/client_bundle/client/fast"),
      AssetCandidatePath("../../examples/js/target/client_bundle/client/fast")
    )
  )
)
