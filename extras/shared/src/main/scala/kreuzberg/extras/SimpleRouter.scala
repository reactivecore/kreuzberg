package kreuzberg.extras

import kreuzberg.*
import kreuzberg.util.Stateful
import kreuzberg.scalatags._
import kreuzberg.scalatags.all._

/**
 * A Simple router implementation.
 * @param routes
 *   the different routes
 * @param notFoundRoute
 *   route to be called, if no route matches
 * @param currentRoute
 *   model for the current route
 * @param titlePrefix
 *   prefix to be added for titles
 */
case class SimpleRouter(
    routes: Vector[Route],
    notFoundRoute: Route,
    titlePrefix: String = ""
) extends ComponentBase {

  case class RoutingState(
      currentRoute: String
  )

  override def assemble: AssemblyResult[Unit] = {
    for {
      routingStateModel <- Model.make[RoutingState]("route", RoutingState(BrowserRouting.getCurrentPath()))
      routingState      <- subscribe(routingStateModel)
      _                  = Logger.debug(s"Rendering SimpleRouter with value ${routingState} on model ${routingStateModel.id}")
      routeValue         = routingState.currentRoute
      id                <- Stateful[AssemblyState, ComponentId](_.ensureChildren(routeValue))
      route              = decideRoute(routeValue)
      assembled         <- route.node(id, routeValue)
      gotoBinding        =
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
          .changeModel(routingStateModel) { (path, model) =>
            model.copy(currentRoute = path)
          }
      onLoadBinding      = EventSource
                             .WindowJsEvent(Event.JsEvent("load"))
                             .changeModel(routingStateModel) { (_, m) =>
                               val path = BrowserRouting.getCurrentPath()
                               Logger.debug(s"Load Event: ${path}")
                               RoutingState(path)
                             }
                             .and
                             .executeCode { _ =>
                               val path  = BrowserRouting.getCurrentPath()
                               val title = decideRoute(path).title(path)
                               BrowserRouting.setDocumentTitle(titlePrefix + title)
                             }
      popStateBinding    = EventSource
                             .WindowJsEvent(Event.JsEvent("popstate"))
                             .changeModel(routingStateModel) { (_, current) =>
                               val path = BrowserRouting.getCurrentPath()
                               Logger.debug(s"Popstate event ${path}")
                               RoutingState(path)
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

  /** Event Sink for going to a specific route. */
  def goto: EventSink[String] = EventSink.ChannelSink(gotoChannel)
}
