package kreuzberg.extras

import kreuzberg.{Identifier, Logger, Model}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/** Encodes the current state within the Router. */
private[extras] sealed trait RoutingState {
  def url: UrlResource

  def result(settings: RouterSettings): RoutingResult
}

private[extras] object RoutingState {

  /**
   * Runs the state machine for a given URL and routes.
   *
   * @param url
   *   url where to go
   * @param routes
   *   available routes
   * @param model
   *   model to update in case of lazy updates.
   */
  def decide(url: UrlResource, routes: Seq[Route], model: Model[RoutingState])(
      using ec: ExecutionContext
  ): RoutingState = {
    val route = routes.find(_.handles(url)) match {
      case Some(found) =>
        found
      case None        =>
        return RoutingState.NotFound(url)
    }

    val routingTarget = route.target(url) match {
      case Right(value) =>
        value
      case Left(error)  =>
        return RoutingState.Failed(url, error)
    }

    routingTarget.forward match {
      case Some(url) =>
        return RoutingState.Forward(url)
      case None      =>
      // continue
    }

    routingTarget match {
      case eager: RoutingResult =>
        RoutingState.Loaded(url, eager, route)
      case lazyRoute            =>
        val loadingId = Identifier.next()

        Router.loading.set(true)

        lazyRoute.load().onComplete { result =>
          Router.loading.set(false)
          onLoaded(url, route, loadingId, result, model)
        }

        RoutingState.Loading(url, lazyRoute, loadingId)
    }
  }

  private def onLoaded(
      url: UrlResource,
      route: Route,
      loadingId: Identifier,
      result: Try[RoutingResult],
      lazyLoadModel: Model[RoutingState]
  ): Unit = {
    // Otherwise the user is probably on the next screen
    // Note: if the callback is fast, the old value may not yet be commited, so we use the eagerValue
    val stateAgain   = lazyLoadModel.eagerValue()
    val continueHere = stateAgain match {
      case l: RoutingState.Loading if l.invocation == loadingId => true
      case _                                                    =>
        Logger.debug(
          "Discarding response of loading, not anymore on the same loading page"
        )
        false
    }
    if (continueHere) {
      val translated = result match {
        case Failure(exception) => RoutingState.Failed(url, Error.fromThrowable(exception))
        case Success(v)         => RoutingState.Loaded(url, v, route)
      }
      lazyLoadModel.set(translated)
    }
  }

  /** Not yet initialized */
  case object Empty extends RoutingState {
    override def url: UrlResource = UrlResource()

    override def toString: String = "empty"

    override def result(settings: RouterSettings): RoutingResult = {
      RoutingResult(
        "",
        settings.loadingHandler(url)
      )
    }
  }

  /**
   * Loading the next state.
   * @param invocation
   *   an id to correlate result to loading, otherwise we may overwrite forwarded states.
   */
  case class Loading(url: UrlResource, routingTarget: RoutingTarget, invocation: Identifier) extends RoutingState {
    override def toString: String = s"loading (url=${url}, invocation=${invocation})"

    override def result(settings: RouterSettings): RoutingResult = RoutingResult(
      title = routingTarget.preTitle,
      component = settings.loadingHandler(url)
    )
  }

  /** Loaded state. */
  case class Loaded(url: UrlResource, result: RoutingResult, route: Route) extends RoutingState {
    override def toString: String = s"loaded ${url}"

    override def result(settings: RouterSettings): RoutingResult = result
  }

  case class Failed(url: UrlResource, error: Error) extends RoutingState {
    override def toString: String = s"failed ${url}: ${error}"

    override def result(settings: RouterSettings): RoutingResult = {
      settings.errorHandler(url, error)
    }
  }

  case class NotFound(url: UrlResource) extends RoutingState {
    override def toString: String = s"not found: ${url}"

    override def result(settings: RouterSettings): RoutingResult = {
      settings.notFoundHandler(url)
    }
  }

  case class Forward(url: UrlResource) extends RoutingState {
    override def result(settings: RouterSettings): RoutingResult = {
      RoutingResult("", settings.loadingHandler(url))
    }
  }
}
