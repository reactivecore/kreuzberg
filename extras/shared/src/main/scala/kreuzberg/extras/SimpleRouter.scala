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
 * @param errorHandler
 *   generic error handler
 * @param loadingHandler
 *   generic loading handler
 */
case class SimpleRouter(
    routes: Vector[Route],
    notFoundRoute: EagerRoute,
    titlePrefix: String = "",
    errorHandler: (UrlResource, Throwable) => Component = SimpleRouter.DefaultErrorHandler,
    loadingHandler: Option[Route] => Component = SimpleRouter.DefaultLoadingHandler
) extends SimpleComponentBase {

  override def assemble(using context: SimpleContext): Html = {
    val routingState = subscribe(SimpleRouter.routingStateModel)
    Logger.debug(s"Assembling SimpleRouter with value ${routingState} on model ${SimpleRouter.routingStateModel.id}")

    def handlePath(pushState: Boolean): EventSink[UrlResource] = {
      EventTransformer
        .Empty[UrlResource]()
        .map { url =>
          url -> decideRoute(url)
        }
        .tap { case (url, nextRoute) =>
          Logger.debug(s"Going to ${url} (route=${nextRoute}, pushState=${pushState})")
          val currentPath = BrowserRouting.getCurrentResource()
          val title       = nextRoute.preTitle(url)
          if (pushState && url != currentPath) {
            Logger.debug(s"Push state ${title}/${url}")
            BrowserRouting.pushState(title, url.str)
          }
          BrowserRouting.setDocumentTitle(titlePrefix + title)
        }
        .map { case (url, route) =>
          decideInitialState(url, route)
        }
        .viaSink(
          EventSink.ModelChange(SimpleRouter.routingStateModel, (e, _) => e)
        )
        .collect { case loading: RoutingState.Loading =>
          loading
        }
        .effect { loading =>
          loading.route.target(loading.url)
        }
        .filter { case (loading, _) =>
          // Otherwise the user is probably on the nxt screen
          val stateAgain = SimpleRouter.routingStateModel.read
          stateAgain match {
            case l: RoutingState.Loading if l.invocation == loading.invocation => true
            case _                                                             =>
              Logger.debug(
                s"Discarding response of loading, not yet on the same loading page"
              )
              false
          }
        }
        .tap { case (_, maybeLoaded) =>
          maybeLoaded.foreach { routingTarget =>
            BrowserRouting.setDocumentTitle(routingTarget.title)
          }
        }
        .map { case (loading, maybeLoaded) =>
          maybeLoaded match {
            case Failure(exception) => RoutingState.Failed(loading.url, loading.route, exception)
            case Success(v)         => RoutingState.Loaded(loading.url, loading.route, v.component)
          }
        }
        .intoModel(SimpleRouter.routingStateModel)
    }

    val (component: Component, url: UrlResource) = routingState match {
      case RoutingState.Empty                         =>
        val url = BrowserRouting.getCurrentResource()
        add(
          EventSource.Assembled
            .map { _ => url }
            .to(handlePath(false))
        )
        (loadingHandler(None), url)
      case RoutingState.Loading(url, route, _)        =>
        (loadingHandler(Some(route)), url)
      case RoutingState.Failed(url, route, error)     =>
        (errorHandler(url, error), url)
      case RoutingState.Loaded(url, route, component) =>
        (component, url)
    }

    add(
      SimpleRouter.gotoChannel.to(handlePath(true))
    )

    add(
      SimpleRouter.reloadChannel.map(_ => url).to(handlePath(false))
    )

    add(
      EventSource.Js
        .window("popstate")
        .map(_ => BrowserRouting.getCurrentResource())
        .to(handlePath(false))
    )

    div(component.wrap)
  }

  private def decideRoute(url: UrlResource): Route = {
    routes.find(_.canHandle(url)).getOrElse(notFoundRoute)
  }

  private def decideInitialState(url: UrlResource, route: Route): RoutingState = {
    route match {
      case eager: EagerRoute =>
        val state     = eager.pathCodec.forceDecode(url)
        val component = eager.component(state)
        RoutingState.Loaded(url, route, component)
      case otherwise         =>
        RoutingState.Loading(url, route, Identifier.next())
    }
  }
}

object SimpleRouter {
  val gotoChannel: Channel[UrlResource] = Channel.create()

  sealed trait RoutingState

  object RoutingState {

    /** Not yet initialized */
    case object Empty extends RoutingState

    /**
     * Loading the next state.
     * @param invocation
     *   an id to correlate result to loading, otherwise we may overwrite forwarded states.
     */
    case class Loading(url: UrlResource, route: Route, invocation: Identifier) extends RoutingState

    /** Loaded state. */
    case class Loaded(url: UrlResource, route: Route, component: Component) extends RoutingState
    case class Failed(url: UrlResource, route: Route, error: Throwable)     extends RoutingState
  }

  private val routingStateModel = Model.create[RoutingState](RoutingState.Empty)

  def loading: Subscribeable[Boolean] = routingStateModel.map {
    case _: RoutingState.Loading => true
    case _                       => false
  }

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

  val DefaultErrorHandler: (UrlResource, Throwable) => Component = { (url, error) =>
    new SimpleComponentBase {
      override def assemble(using c: SimpleContext): Html = {
        h2("Error")
        div(s"An unrecoverable error handled on loading route ${url}: ${error.getMessage}")
      }
    }
  }

  val DefaultLoadingHandler: Option[Route] => Component = route => {
    EmptyComponent
  }
}
