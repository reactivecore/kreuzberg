package kreuzberg.miniserver
import kreuzberg.*
import kreuzberg.rpc.Dispatcher

/**
 * Configuration for MiniServer.
 * @tparam F
 *   Effect Type, can be Id.
 */
case class MiniServerConfig[F[_]](
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
    api: Option[F[Dispatcher[F]]] = None,
    noScriptText: Option[String] = None // if not given, use default.
) {
  def hashedUrl(name: String): String = assetPaths.hashedUrl(name, deploymentType)

  def locateAsset(name: String): Option[Location] = assetPaths.locateAsset(name: String, deploymentType)
}
