package kreuzberg.miniserver

enum DeploymentType {
  case Debug, Production
}

object DeploymentType {
  def isCompatible(assetType: Option[DeploymentType], expected: Option[DeploymentType]): Boolean = {
    (assetType, expected) match {
      case (None, _)          => true
      case (_, None)          => true
      case (Some(a), Some(b)) => a == b
    }
  }
}
