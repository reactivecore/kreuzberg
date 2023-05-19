package kreuzberg.extras

import kreuzberg.*
import kreuzberg.extras.SimpleRouter.RoutingState
import kreuzberg.util.Stateful
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/**
 * A Simple router implementation.
 * @param routes
 *   the different routes
 * @param notFoundRoute
 *   route to be called, if no route matches
 * @param titlePrefix
 *   prefix to be added for titles
 */
case class SimpleRouter(
    routes: Vector[Route],
    notFoundRoute: Route,
    titlePrefix: String = ""
) extends ComponentBase {

  override def assemble: AssemblyResult[Unit] = {
    for {
      routingState   <- subscribe(SimpleRouter.routingStateModel)
      _               =
        Logger.debug(s"Rendering SimpleRouter with value ${routingState} on model ${SimpleRouter.routingStateModel.id}")
      routeValue      = routingState.currentRoute.getOrElse(BrowserRouting.getCurrentPath())
      id             <- Stateful[AssemblyState, ComponentId](_.ensureChildren(routeValue))
      route           = decideRoute(routeValue)
      assembled      <- route.node(id, routeValue)
      gotoBinding     =
        EventSource
          .ChannelSource(SimpleRouter.gotoChannel)
          .executeCode { target =>
            val title = decideRoute(target).title(target)
            Logger.debug(s"Model change ${routingState.currentRoute} -> ${target}")
            val path  = BrowserRouting.getCurrentPath()
            if (target != path) { // Otherwise we would also push a state change on a pop change
              Logger.debug(s"Push state ${title}/${target}")
              BrowserRouting.pushState(title, target)
            }
            BrowserRouting.setDocumentTitle(titlePrefix + title)
          }
          .and
          .changeModel(SimpleRouter.routingStateModel) { (path, model) =>
            model.copy(currentRoute = Some(path))
          }
      onLoadBinding   = EventSource
                          .WindowJsEvent(Event.JsEvent("load"))
                          .changeModel(SimpleRouter.routingStateModel) { (_, m) =>
                            val path = BrowserRouting.getCurrentPath()
                            Logger.debug(s"Load Event: ${path}")
                            RoutingState(Some(path))
                          }
                          .and
                          .executeCode { _ =>
                            val path  = BrowserRouting.getCurrentPath()
                            val title = decideRoute(path).title(path)
                            BrowserRouting.setDocumentTitle(titlePrefix + title)
                          }
      popStateBinding = EventSource
                          .WindowJsEvent(Event.JsEvent("popstate"))
                          .changeModel(SimpleRouter.routingStateModel) { (_, current) =>
                            val path = BrowserRouting.getCurrentPath()
                            Logger.debug(s"Popstate event ${path}")
                            RoutingState(Some(path))
                          }

    } yield {
      Assembly(
        div(assembled.wrap),
        Vector(
          gotoBinding,
          onLoadBinding,
          popStateBinding
        ),
        provider = _ => ()
      )
    }
  }

  private def decideRoute(path: String): Route = {
    routes.find(_.canHandle(path)).getOrElse(notFoundRoute)
  }
}

object SimpleRouter {
  val gotoChannel: Channel[String] = Channel.create()

  case class RoutingState(
      currentRoute: Option[String] = None
  )

  val routingStateModel = Model.create(RoutingState())

  /** Event Sink for going to a specific route. */
  def goto: EventSink[String] = EventSink.ChannelSink(gotoChannel)

  /** Event Sink for going to a specific fixed route. */
  def gotoTarget(target: String): EventSink[Any] = goto.contraMap(_ => target)
}
