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

  override def assemble: AssemblyResult = {
    for {
      routingStateModel <- provide[Model[RoutingState]]
      routingState      <- subscribe(routingStateModel)
      _                  = Logger.debug(s"Rendering SimpleRouter with value ${routingState} on model ${routingStateModel.id}")
      routeValue         = routingState.currentRoute
      id                <- Stateful[AssemblyState, ComponentId](_.ensureChildren(routeValue))
      route              = decideRoute(routeValue)
      assembled         <- route.node(id, routeValue)
      onLoadBinding      = EventBinding(
                             EventSource.WindowJsEvent(Event.JsEvent("load")),
                             EventSink
                               .ModelChange(
                                 routingStateModel,
                                 (_, m) => {
                                   val path = BrowserRouting.getCurrentPath()
                                   Logger.debug(s"Load Event: ${path}")
                                   RoutingState(path)
                                 }
                               )
                               .and(EventSink.Custom { _ =>
                                 val path  = BrowserRouting.getCurrentPath()
                                 val title = decideRoute(path).title(path)
                                 BrowserRouting.setDocumentTitle(titlePrefix + title)
                               })
                           )
      routeChangeBinding = EventBinding(
                             EventSource.ModelChange(routingStateModel),
                             EventSink.Custom[(RoutingState, RoutingState)] { case (from, target) =>
                               val title = decideRoute(target.currentRoute).title(target.currentRoute)
                               Logger.debug(s"Model change ${from} -> ${target}")
                               val path  = BrowserRouting.getCurrentPath()
                               if (target.currentRoute != path) { // Otherwise we would also push a state change on a pop change
                                 Logger.debug(s"Push state ${title}/${target}")
                                 BrowserRouting.pushState(title, target.currentRoute)
                               }
                               BrowserRouting.setDocumentTitle(titlePrefix + title)
                             }
                           )
      popStateBinding    = EventBinding(
                             EventSource.WindowJsEvent(Event.JsEvent("popstate")),
                             EventSink.ModelChange(
                               routingStateModel,
                               { (_, current) =>
                                 val path = BrowserRouting.getCurrentPath()
                                 Logger.debug(s"Popstate event ${path}")
                                 RoutingState(path)
                               }
                             )
                           )
    } yield {
      Assembly(
        div(),
        Vector(assembled),
        Vector(
          onLoadBinding,
          routeChangeBinding,
          popStateBinding
        )
      )
    }
  }

  private def decideRoute(path: String): Route = {
    routes.find(_.canHandle(path)).getOrElse(notFoundRoute)
  }
}
