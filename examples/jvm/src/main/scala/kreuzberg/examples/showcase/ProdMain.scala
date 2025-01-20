package kreuzberg.examples.showcase

import kreuzberg.miniserver.DeploymentType.Production

import scala.annotation.experimental

@experimental
object ProdMain extends App {
  ServerMainLoom(deploymentConfig =
    defaultDeploymentConfig.copy(
      deploymentType = Production
    )
  ).run()
}
