package kreuzberg.extras

import kreuzberg.*

import language.implicitConversions
import scala.concurrent.{ExecutionContext, Future}

/** A Route within [[MainRouter]] */
trait Route {

  /** Returns true if this route handles the resource. */
  def handles(url: UrlResource): Boolean

  /** Returns the target for this URL. */
  def target(url: UrlResource): Result[RoutingTarget]

  /**
   * Updates this Route with an updated Resource.
   *
   * When it returns true, the route won't be updated.
   *
   * Used for Sub routers, so that they do not flicker.
   */
  def update(url: UrlResource): Boolean = false
}

/** A Route which has a codec inside. */
trait RouteWithCodec[State] extends Route {

  /** The path codec to map state and path */
  def codec: PathCodec[State]

  final override def handles(url: UrlResource): Boolean = codec.handles(url)

  /** Directly encode a state into a path. */
  final def url(state: State): UrlResource = codec.encode(state)

  /** Returns the URL f there is no state. */
  final def url(using ev: Unit =:= State): UrlResource = url(ev(()))

  /** Returns the target for the route. */
  def target(state: State): RoutingTarget

  /** Returns the target for a given URL. */
  def target(url: UrlResource): Result[RoutingTarget] = {
    codec.decode(url).map(target)
  }
}

object Route {

  /** Simplification for building routing tables. */
  implicit def routedToRoute(routed: Routed): Route = routed.route

  /** Simple Route without parameters. */
  case class SimpleRoute(
      path: UrlPath,
      result: RoutingResult
  ) extends RouteWithCodec[Unit] {
    override val codec: PathCodec[Unit] = PathCodec.const(path)

    override def target(state: Unit): RoutingResult = result
  }

  case class SimpleForward(
      path: UrlPath,
      destination: UrlResource
  ) extends RouteWithCodec[Unit] {
    override def codec: PathCodec[Unit] = PathCodec.const(path)

    override def target(state: Unit): Forward = Forward(destination)
  }

  /** A eager Route whose target depends on a value. */
  case class SimpleDependentRoute[S](
      override val codec: PathCodec[S],
      fn: S => RoutingResult
  ) extends RouteWithCodec[S] {

    override def target(state: S): RoutingResult = fn(state)
  }

  /** A Route which depends upon loading data. */
  case class LazyRoute[S](
      override val codec: PathCodec[S],
      eagerTitle: S => String,
      fn: S => Future[RoutingResult],
      meta: MetaData = MetaData.empty
  ) extends RouteWithCodec[S] {

    override def target(state: S): RoutingTarget = {
      new RoutingTarget {
        override def preTitle: String = eagerTitle(state)

        override def metaData: MetaData = meta

        override def load(): Future[RoutingResult] = fn(state)
      }
    }
  }
}
