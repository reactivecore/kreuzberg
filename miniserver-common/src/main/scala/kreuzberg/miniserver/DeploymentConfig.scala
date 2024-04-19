package kreuzberg.miniserver

import kreuzberg.Html
import kreuzberg.miniserver.{AssetPaths, DeploymentType}

/** Deployment related configuration */
case class DeploymentConfig(
    assetPaths: AssetPaths,
    extraJs: Seq[String] = Nil,
    extraCss: Seq[String] = Nil,
    extraHtmlHeader: Seq[Html] = Nil,
    deploymentType: Option[DeploymentType] = None,
    produktionBlacklist: Seq[String] = Seq(
      ".*\\.js\\.map",
      ".*\\.css\\.map"
    ),
    noScriptText: Option[String] = None // if not given, use default.
) {
  def hashedUrl(name: String): String = assetPaths.hashedUrl(name, deploymentType)

  def locateAsset(name: String): Option[Location] = assetPaths.locateAsset(name: String, deploymentType)
}
