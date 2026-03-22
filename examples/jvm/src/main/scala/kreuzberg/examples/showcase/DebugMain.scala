package kreuzberg.examples.showcase

import kreuzberg.miniserver.DeploymentType.Debug

import scala.annotation.experimental

@experimental
object DebugMain {
  def main(args: Array[String]): Unit = {
    ServerMainLoom(deploymentConfig =
      defaultDeploymentConfig.copy(
        deploymentType = Debug
      )
    ).run()
  }
}
