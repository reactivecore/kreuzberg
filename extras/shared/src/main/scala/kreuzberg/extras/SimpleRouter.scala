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

    val routeValue = routingState.currentRoute.getOrElse(BrowserRouting.getCurrentResource())
    val route      = decideRoute(routeValue)
    subscribe(SimpleRouter.currentTarget)

    val target = route match {
      case e: EagerRoute => e.eagerTarget(routeValue)
      case _             => read(SimpleRouter.currentTarget)
    }

    def handlePath(in: EventSource[UrlResource], pushState: Boolean): EventBinding = {
      in.map { path => path -> decideRoute(path) }
        .executeCode { case (path, nextRoute) =>
          val currentPath = BrowserRouting.getCurrentResource()
          val title       = nextRoute.preTitle(path)
          if (pushState && path != currentPath) {
            Logger.debug(s"Push state ${title}/${path}")
            BrowserRouting.pushState(title, path.str)
          }
          BrowserRouting.setDocumentTitle(titlePrefix + title)
        }
        .and
        .setModelTo(SimpleRouter.loading, true)
        .and
        .effect { case (path, route) =>
          route.target(path)
        }
        .setModelTo(SimpleRouter.loading, false)
        .and
        .map {
          case ((path, _), Success(target)) => (path, target)
          case ((path, _), Failure(error))  => (path, errorHandler(error).eagerTarget(path))
        }
        .executeCode { case (path, target) =>
          val title = target.title
          BrowserRouting.replaceState(title, path.str)
          BrowserRouting.setDocumentTitle(titlePrefix + title)
        }
        .and
        .changeModel(SimpleRouter.routingStateModel) { case ((path, target), model) =>
          model.copy(currentRoute = Some(path))
        }
        .and
        .map(_._2)
        .intoModel(SimpleRouter.currentTarget)
    }

    add(
      SimpleRouter.gotoChannel.transform(handlePath(_, true))
    )

    add(
      SimpleRouter.reloadChannel
        .map { _ => routeValue }
        .transform(handlePath(_, false))
    )

    add(
      EventSource.Js
        .window("load")
        .map { _ => BrowserRouting.getCurrentResource() }
        .transform(handlePath(_, false))
    )

    add(
      EventSource.Js
        .window("popstate")
        .changeModel(SimpleRouter.routingStateModel) { (_, current) =>
          val path = BrowserRouting.getCurrentResource()
          Logger.debug(s"Popstate event ${path}")
          RoutingState(Some(path))
        }
    )

    div(target.component.wrap)
  }

  private def decideRoute(resource: UrlResource): Route = {
    routes.find(_.canHandle(resource)).getOrElse(notFoundRoute)
  }
}

object SimpleRouter {
  val gotoChannel: Channel[UrlResource] = Channel.create()

  case class RoutingState(
      currentRoute: Option[UrlResource] = None
  )

  /** Current routing state. */
  val routingStateModel = Model.create(RoutingState())

  /** Current shown target. */
  val currentTarget = Model.create[RoutingTarget](RoutingTarget("", EmptyComponent))

  /** If we are currently loading data. */
  val loading = Model.create[Boolean](false)

  /** Event Sink for going to a specific route. */
  def goto: EventSink[UrlResource] = EventSink.ChannelSink(gotoChannel)

  /** Force a reload. */
  val reloadChannel: Channel[Any] = Channel.create()
  def reload: EventSink[Any]      = EventSink.ChannelSink(reloadChannel)

  /** Event Sink for going to a specific fixed route. */
  def gotoTarget(target: UrlResource): EventSink[Any] = goto.contraMap(_ => target)

  /** Event sink for going to root (e.g. on logout) */
  def gotoRoot(): EventSink[Any] = goto.contraMap(_ => UrlResource("/"))

  case object EmptyComponent extends SimpleComponentBase {
    override def assemble(using c: SimpleContext): Html = {
      div("Loading...")
    }
  }

  val DefaultErrorHandler: Throwable => EagerRoute = error => {
    new EagerRoute {
      override type State = UrlResource

      override val pathCodec: PathCodec[UrlResource] = PathCodec.all

      override def title(path: UrlResource): String = "Error"

      override def component(resource: UrlResource): Component = new SimpleComponentBase {
        override def assemble(using c: SimpleContext): Html = {
          h2("Error")
          div(s"An unrecoverable error handled on loading route ${resource}: ${error.getMessage}")
        }
      }

      override def canHandle(resource: UrlResource): Boolean = true
    }
  }
}
