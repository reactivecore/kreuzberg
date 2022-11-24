package kreuzberg.miniserver
import kreuzberg._

case class MiniServerConfig(
    assetPaths: AssetPaths,
    extraJs: Seq[String] = Nil,
    extraCss: Seq[String] = Nil,
    extraHtmlHeader: Seq[Html] = Nil,
    port: Int = 8090,
    deploymentType: Option[DeploymentType] = None,
    produktionBlacklist: Seq[String] = Seq(
      ".*\\.js\\.map",
      ".*\\.css\\.map"
    )
) {
  def hashedUrl(name: String): String = assetPaths.hashedUrl(name, deploymentType)

  def locateAsset(name: String): Option[Location] = assetPaths.locateAsset(name: String, deploymentType)
}
