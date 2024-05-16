package kreuzberg.miniserver

import kreuzberg.{Html, miniserver}
import kreuzberg.miniserver.{AssetPaths, DeploymentType}

/** Deployment related configuration */
case class DeploymentConfig(
    assetPaths: AssetPaths,
    extraJs: Seq[String] = Nil,
    extraCss: Seq[String] = Nil,
    extraHtmlHeader: Seq[Html] = Nil,
    deploymentType: DeploymentType = DeploymentType.Debug,
    produktionBlacklist: Seq[String] = Seq(
      ".*\\.js\\.map",
      ".*\\.css\\.map"
    ),
    noScriptText: Option[String] = None // if not given, use default.
) {
  def hashedUrl(name: String): String = assetPaths.hashedUrl(name, Some(deploymentType))

  private val blacklistRegexes = produktionBlacklist.map(_.r)

  private def isBlacklisted(s: String): Boolean = {
    deploymentType match {
      case miniserver.DeploymentType.Debug      => false
      case miniserver.DeploymentType.Production => blacklistRegexes.exists(_.matches(s))
    }
  }

  def locateAsset(name: String): Option[Location] = {
    if (isBlacklisted(name)) {
      None
    } else {
      assetPaths.locateAsset(name: String, Some(deploymentType))
    }
  }
}
