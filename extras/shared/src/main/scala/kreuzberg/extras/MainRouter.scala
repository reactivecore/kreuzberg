package kreuzberg.extras

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/**
 * The main router implementation.
 *
 * @param routes
 *   the different routes
 * @param settings
 *   the settings
 */
case class MainRouter(
    routes: Vector[Route],
    settings: RouterSettings
) extends SimpleComponentBase {

  /** The current Routing State. */
  private[extras] val routingStateModel = Model.create[RoutingState](RoutingState.Empty)

  override def assemble(using sc: SimpleContext): Html = {
    val routingState = subscribe(routingStateModel)
    Logger.debug(s"Assembling RouterBase with value ${routingState} on model ${routingStateModel.id}")

    val result = routingState.result(settings)

    routingState match {
      case RoutingState.Empty      =>
        val url = BrowserRouting.getCurrentResource()
        handlePath(false, url)
      case f: RoutingState.Forward =>
        Router.goto(f.url)
      case _                       =>
    }

    addHandler(Router.gotoChannel) { url =>
      handlePath(true, url)
    }

    addHandlerAny(Router.reloadChannel) {
      handlePath(false, Router.currentUrl.read())
    }

    addHandler(Router.requestTitle) { title =>
      BrowserRouting.setDocumentTitle(settings.titlePrefix + title)
    }

    addHandlerAny(EventSource.Js.window("popstate")) {
      val url = BrowserRouting.getCurrentResource()
      handlePath(false, url)
    }

    BrowserRouting.setDocumentTitle(settings.titlePrefix + result.title)
    MetaUtil.injectMetaData(result.meta)

    div(result.component)
  }

  /**
   * Handle a Path change request
   *
   * @param pushState
   *   also push state to Browser History
   * @param url
   *   the URL where to go to.
   */
  protected def handlePath(pushState: Boolean, url: UrlResource): Unit = {
    Router.currentUrl.set(url)

    routingStateModel.read() match {
      case RoutingState.Loaded(_, _, route) if route.update(url) =>
        // The route can update it's stuff by itself, stop here now
        return
      case _                                                     =>
      // Nothing
    }

    val state = RoutingState.decide(url, routes, routingStateModel)
    routingStateModel.set(state)

    if (pushState) {
      val intermediate = state.result(settings)
      pushUrl(url, intermediate)
    }
  }

  private def pushUrl(url: UrlResource, routingTarget: RoutingTarget): Unit = {
    val currentPath = BrowserRouting.getCurrentResource()

    if (url != currentPath) {
      Logger.debug(s"Push state ${routingTarget.preTitle}/${url}")
      BrowserRouting.pushState(routingTarget.preTitle, url.str)
    }
  }
}
