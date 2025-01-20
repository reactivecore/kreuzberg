package kreuzberg.examples.showcase

import kreuzberg.miniserver.DeploymentType.Debug

import scala.annotation.experimental

@experimental
object DebugMain extends App {
  ServerMainLoom(deploymentConfig =
    defaultDeploymentConfig.copy(
      deploymentType = Debug
    )
  ).run()
}
