package kreuzberg.extras

import kreuzberg.*
import kreuzberg.extras.Route.EagerRoute
import kreuzberg.extras.SimpleRouter.RoutingState
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

import scala.util.{Failure, Success}

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
    notFoundRoute: EagerRoute,
    titlePrefix: String = "",
    errorHandler: Throwable => EagerRoute = SimpleRouter.DefaultErrorHandler
) extends SimpleComponentBase {

  override def assemble(using context: SimpleContext): Html = {
    val routingState = subscribe(SimpleRouter.routingStateModel)
    Logger.debug(s"Assembling SimpleRouter with value ${routingState} on model ${SimpleRouter.routingStateModel.id}")

    val routeValue = routingState.currentRoute.getOrElse(BrowserRouting.getCurrentPath())
    val route      = decideRoute(routeValue)
    val target     = route match {
      case e: EagerRoute => e.eagerTarget(routeValue)
      case _             => subscribe(SimpleRouter.currentTarget)
    }

    def handlePath(in: EventSource[String], pushState: Boolean): EventBinding = {
      in.map { path => path -> decideRoute(path) }
        .executeCode { case (path, nextRoute) =>
          val currentPath = BrowserRouting.getCurrentPath()
          val title       = nextRoute.preTitle(path)
          if (pushState && path != currentPath) {
            Logger.debug(s"Push state ${title}/${path}")
            BrowserRouting.pushState(title, path)
          }
          BrowserRouting.setDocumentTitle(titlePrefix + title)
        }
        .and
        .setModel(SimpleRouter.loading, true)
        .and
        .effect { case (path, route) =>
          route.target(path)
        }
        .setModel(SimpleRouter.loading, false)
        .and
        .map {
          case ((path, _), Success(target)) => (path, target)
          case ((path, _), Failure(error))  => (path, errorHandler(error).eagerTarget(path))
        }
        .executeCode { case (path, target) =>
          val title = target.title
          BrowserRouting.replaceState(title, path)
          BrowserRouting.setDocumentTitle(titlePrefix + title)
        }
        .and
        .changeModel(SimpleRouter.routingStateModel) { case ((path, target), model) =>
          model.copy(currentRoute = Some(path))
        }
        .and
        .tap { foo => println(s"${foo}") }
        .intoModel(SimpleRouter.currentTarget)(_._2)
    }

    add(
      SimpleRouter.gotoChannel.transform(handlePath(_, true))
    )

    add(
      EventSource.Js
        .window("load")
        .map { _ => BrowserRouting.getCurrentPath() }
        .transform(handlePath(_, false))
    )

    add(
      EventSource.Js
        .window("popstate")
        .changeModel(SimpleRouter.routingStateModel) { (_, current) =>
          val path = BrowserRouting.getCurrentPath()
          Logger.debug(s"Popstate event ${path}")
          RoutingState(Some(path))
        }
    )

    div(target.component.wrap)
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

  /** Current routing state. */
  val routingStateModel = Model.create(RoutingState())

  /** Current shown target. */
  val currentTarget = Model.create[RoutingTarget](RoutingTarget("", EmptyComponent))

  /** If we are currently loading data. */
  val loading = Model.create[Boolean](false)

  /** Event Sink for going to a specific route. */
  def goto: EventSink[String] = EventSink.ChannelSink(gotoChannel)

  /** Event Sink for going to a specific fixed route. */
  def gotoTarget(target: String): EventSink[Any] = goto.contraMap(_ => target)

  case object EmptyComponent extends SimpleComponentBase {
    override def assemble(using c: SimpleContext): Html = {
      div("Loading...")
    }
  }

  val DefaultErrorHandler: Throwable => EagerRoute = error => {
    new EagerRoute {
      override type State = String

      override val pathCodec: PathCodec[String] = PathCodec.all

      override def title(path: String): String = "Error"

      override def component(path: String): Component = new SimpleComponentBase {
        override def assemble(using c: SimpleContext): Html = {
          h2("Error")
          div(s"An unrecoverable error handled on loading route ${path}: ${error.getMessage}")
        }
      }

      override def canHandle(path: String): Boolean = true
    }
  }
}
