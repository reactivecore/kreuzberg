package kreuzberg.examples.showcase

import kreuzberg.miniserver.DeploymentType.Production

import scala.annotation.experimental

@experimental
object ProdMain {
  def main(args: Array[String]): Unit = {
    ServerMainLoom(deploymentConfig =
      defaultDeploymentConfig.copy(
        deploymentType = Production
      )
    ).run()
  }
}
