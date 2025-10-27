package kreuzberg.extras

import kreuzberg.*


import language.implicitConversions
import scala.concurrent.Future

/** A Route within [[SimpleRouter]] */
trait Route {

  /** The state of which the route depends on. */
  type State

  /** The path codec to map state and path */
  def pathCodec: PathCodec[State]

  /** Directly encode a state into a path. */
  def url(state: State): UrlResource = pathCodec.encode(state)

  /** Returns true if the route can handle a given path. */
  def canHandle(resource: UrlResource): Boolean

  /** Title displayed while loading. */
  def preTitle(resource: UrlResource): String

  /** Execute the route, can load lazy. */
  def target(resource: UrlResource): Future[RoutingTarget]

  /** Provides `MetaData` that should get injected into <header> */
  def metaData: MetaData = MetaData.empty
}

case class RoutingTarget(
    title: String,
    component: Component
)

/** Marks something as being routed. */
trait Routed[T] {
  def route: Route.Aux[T]

  /** Accessing the URL directly from a state. */
  inline def url(state: T): UrlResource = route.url(state)

  /** Accessing the URL directly if there is no state */
  inline def url(implicit ev: Unit => T): UrlResource = url(ev(()))
}

/** Mark some component as simple routed without further state */
trait SimpleRouted extends Routed[Unit] {
  self: Component =>

  /** The routing path */
  def path: String

  /** The URL Resource for this. */
  final def url: UrlResource = UrlResource(path)

  /** The page title */
  def title: String

  /** The metadata of the page. */
  def metaData: MetaData = MetaData.empty

  override val route: Route.Aux[Unit] = Route.SimpleRoute(path, title, this, metaData)
}

object Route {

  type Aux[T] = Route {
    type State = T
  }

  /** Simplification for building routing tables. */
  implicit def routedToRoute[T](routed: Routed[T]): Route.Aux[T] = routed.route

  /** A Route which directly translates a path into a component */
  trait EagerRoute extends Route {
    val pathCodec: PathCodec[State]

    override def canHandle(resource: UrlResource): Boolean = pathCodec.handles(resource)

    override def target(resource: UrlResource): Future[RoutingTarget] = Future.successful {
      eagerTarget(resource)
    }

    def eagerTarget(resource: UrlResource): RoutingTarget = {
      val state = pathCodec.decode(resource).getOrElse {
        throw new IllegalStateException(s"Unmatched path ${resource}")
      }
      RoutingTarget(title(state), component(state))
    }

    /** Returns a title for that path. */
    def title(state: State): String

    override def preTitle(resource: UrlResource): String = eagerTarget(resource).title

    /** Assembles a component for a given path. */
    def component(state: State): Component
  }

  /** Simple Route without parameters. */
  case class SimpleRoute(
      path: String,
      title: String,
      component: Component,
      override val metaData: MetaData = MetaData.empty
  ) extends EagerRoute {
    override type State = Unit

    val pathCodec: PathCodec[Unit] = PathCodec.const(path)

    override def title(state: Unit): String = title

    override def component(state: Unit): Component = component


  }

  /** A Route whose target depends on a value. */
  case class DependentRoute[S](
      pathCodec: PathCodec[S],
      fn: S => Component,
      titleFn: S => String,
      override val metaData: MetaData = MetaData.empty
  ) extends EagerRoute {

    override type State = S

    override def title(state: S): String = titleFn(state)

    override def component(state: S): Component = fn(state)

  }

  case class LazyRoute[S](
      pathCodec: PathCodec[S],
      eagerTitle: S => String,
      routingTarget: S => Future[RoutingTarget],
      override val metaData: MetaData = MetaData.empty
  ) extends Route {
    override type State = S

    override def canHandle(resource: UrlResource): Boolean = pathCodec.handles(resource)

    override def target(resource: UrlResource): Future[RoutingTarget] = {
      routingTarget(pathCodec.forceDecode(resource))
    }

    override def preTitle(resource: UrlResource): String = {
      eagerTitle(pathCodec.forceDecode(resource))
    }

  }
}
