package kreuzberg.miniserver
import kreuzberg.*
import zio.{Task, ZIO}

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
    ),
    api: Option[Task[ZioDispatcher]] = None,
    noScriptText: Option[String] = None // if not given, use default.
) {
  def hashedUrl(name: String): String = assetPaths.hashedUrl(name, deploymentType)

  def locateAsset(name: String): Option[Location] = assetPaths.locateAsset(name: String, deploymentType)
}
