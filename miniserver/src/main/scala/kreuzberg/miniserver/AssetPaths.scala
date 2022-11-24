package kreuzberg.miniserver

case class AssetPaths(
    assetPaths: Seq[RestrictedAssetCandidatePath]
) {

  /** Locate an asset. */
  def locateAsset(name: String, deploymentType: Option[DeploymentType]): Option[Location] = {
    assetPaths.view
      .filter(x => DeploymentType.isCompatible(x.deploymentType, deploymentType))
      .flatMap(_.path.locate(name))
      .headOption
  }

  /**
   * Returns the external URL of an Asset
   */
  def hashedUrl(asset: String, deploymentType: Option[DeploymentType]): String = {
    val ensureSlash = if (asset.startsWith("/")) {
      "/" + asset
    } else asset

    locateAsset(ensureSlash, deploymentType)
      .map { location =>
        val hash = location.hashSha256()
        AssetPaths.AssetUrlPrefix + asset + s"?hash=${hash}"
      }
      .getOrElse(AssetPaths.AssetUrlPrefix + asset)
  }
}

object AssetPaths {
  val AssetUrlPrefix = "/assets/"
}
