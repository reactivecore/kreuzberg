package kreuzberg.examples.showcase

import kreuzberg.miniserver.{AssetCandidatePath, AssetPaths, DeploymentConfig}

val deploymentConfig = DeploymentConfig(
  AssetPaths(
    Seq(
      AssetCandidatePath("examples/js/target/client_bundle/client/fast"),
      AssetCandidatePath("../examples/js/target/client_bundle/client/fast"),
      AssetCandidatePath("../../examples/js/target/client_bundle/client/fast")
    )
  )
)
