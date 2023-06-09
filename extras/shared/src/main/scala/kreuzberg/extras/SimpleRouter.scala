package kreuzberg.extras

import kreuzberg.*
import kreuzberg.extras.SimpleRouter.RoutingState
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

  override def assemble(using context: AssemblerContext): Assembly = {
    val routingState = read(SimpleRouter.routingStateModel)
    Logger.debug(s"Rendering SimpleRouter with value ${routingState} on model ${SimpleRouter.routingStateModel.id}")

    val routeValue = routingState.currentRoute.getOrElse(BrowserRouting.getCurrentPath())
    val route      = decideRoute(routeValue)
    val component  = route.component(routeValue)

    val gotoBinding =
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

    val onLoadBinding = EventSource.Js
      .window("load")
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

    val popStateBinding = EventSource.Js
      .window("popstate")
      .changeModel(SimpleRouter.routingStateModel) { (_, current) =>
        val path = BrowserRouting.getCurrentPath()
        Logger.debug(s"Popstate event ${path}")
        RoutingState(Some(path))
      }

    Assembly(
      div(component.wrap),
      handlers = Vector(
        gotoBinding,
        onLoadBinding,
        popStateBinding
      ),
      subscriptions = Vector(SimpleRouter.routingStateModel)
    )
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
