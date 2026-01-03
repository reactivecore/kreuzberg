package kreuzberg.extras

/** Marks something as being routed. */
trait Routed {
  def route: Route
}

/** Marks something as being routed, having some URL State. */
trait RoutedWithCodec[S] extends Routed {
  def route: RouteWithCodec[S]

  def url(state: S): UrlResource = route.url(state)
}

/** Mark some component as simple routed without further state */
trait SimpleRouted extends Page with Routed {

  /** The routing path */
  def path: UrlPath

  /** The URL Resource for this. */
  final def url: UrlResource = UrlResource(path)

  override val route: RouteWithCodec[Unit] = Route.SimpleRoute(path, this)
}
