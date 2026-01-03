package kreuzberg.extras

import kreuzberg.*
import kreuzberg.extras.RoutingResult.fromPage
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/** An additional sub router, which has it's own Paths. */
class SubRouter(
    prefix: UrlPath,
    routes: Vector[Route],
    settings: RouterSettings = RouterSettings()
) extends SimpleComponentBase {

  private val routingState: Model[RoutingState] = Model.create(RoutingState.Empty)

  override def assemble(using simpleContext: SimpleContext): Html = {
    val state = routingState.subscribe()

    Logger.debug(
      s"SubRouter assembling on ${state}, currentUrl: ${Router.currentUrl.read()}  -> ${Router.currentUrl.eagerValue()}"
    )

    state match {
      case RoutingState.Empty      =>
        goto(Router.currentUrl.read(), true)
      case f: RoutingState.Forward =>
        val full = f.url.prependPath(prefix)
        Router.goto(full)
      case _                       =>
      //
    }

    addHandler(Router.gotoChannel) { url =>
      goto(url, false)
    }

    addHandlerAny(Router.reloadChannel) {
      goto(Router.currentUrl.read(), true)
    }

    val result = state.result(settings)
    Router.requestTitle(settings.titlePrefix + result.title)

    div(
      result.component
    )
  }

  /** Resolve a sub page path. */
  def resolve(url: UrlResource): UrlResource = {
    url.prependPath(prefix)
  }

  /** Make a route for this SubRouter. */
  def makeRoute(surrounder: UrlResource => Result[RoutingTarget]): Route = new Route {
    override def handles(url: UrlResource): Boolean = url.path.startsWith(prefix)

    override def target(url: UrlResource): Result[RoutingTarget] = surrounder(url)

    override def update(url: UrlResource): Boolean = url.path.startsWith(prefix)
  }

  /** Creates an Url for this subrouter. */
  def baseUrl: UrlResource = UrlResource(prefix)

  def makeSimpleRoute(surrounder: Page): Route = makeRoute(_ => Right(surrounder))

  private def goto(url: UrlResource, refresh: Boolean): Unit = {
    if (!refresh && url == routingState.eagerValue().url) {
      return
    }
    val state = decide(url)
    routingState.set(state)
  }

  private def decide(url: UrlResource): RoutingState = {
    val subUrl = url.path.stripPrefix(prefix) match {
      case Some(remainder) =>
        url.copy(path = remainder)
      case None            =>
        return RoutingState.NotFound(url)
    }

    RoutingState.decide(subUrl, routes, routingState)
  }
}
