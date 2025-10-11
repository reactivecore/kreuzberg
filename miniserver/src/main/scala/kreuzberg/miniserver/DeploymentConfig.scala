package kreuzberg.miniserver

import kreuzberg.{Html, miniserver}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/** Deployment related configuration */
case class DeploymentConfig(
    // Served within assets/
    assetPaths: AssetPaths = AssetPaths(),
    // Served within /
    rootAssets: AssetPaths = AssetPaths(),
    extraJs: Seq[String] = Nil,
    extraCss: Seq[String] = Nil,
    extraHtmlHeader: Seq[Html] = Nil,
    deploymentType: DeploymentType = DeploymentType.Debug,
    produktionBlacklist: Seq[String] = Seq(
      ".*\\.js\\.map",
      ".*\\.css\\.map"
    ),
    noScriptText: Option[String] = None, // if not given, use default.
    // Attributes for the root <html>
    htmlRootAttributes: Seq[Modifier] = Seq()
) {
  def hashedUrl(name: String): String = assetPaths.hashedUrl(name, Some(deploymentType))

  private val blacklistRegexes = produktionBlacklist.map(_.r)

  /** Check if a given path is blacklisted for being served as Asset. */
  def isBlacklisted(s: String): Boolean = {
    deploymentType match {
      case DeploymentType.Debug      => false
      case DeploymentType.Production => blacklistRegexes.exists(_.matches(s))
    }
  }
}
