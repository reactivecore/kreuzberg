package kreuzberg.extras

import kreuzberg._
import language.implicitConversions

/** A Route within [[SimpleRouter]] */
trait Route {

  /** The state of which the route depends of. */
  type State

  /** The path codec to map state and path */
  def pathCodec: PathCodec[State]

  /** Directly encode a state into a path. */
  def url(state: State): UrlResource = pathCodec.encode(state)

  /** Returns true if the route can handle a given path. */
  def canHandle(resource: UrlResource): Boolean

  /** Title displayed while loading. */
  def preTitle(resource: UrlResource)(using AssemblerContext): String

  /** Execute the route, can load lazy. */
  def target(resource: UrlResource)(using AssemblerContext): Effect[RoutingTarget]
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

  /** The page title */
  def title: String

  override val route: Route.Aux[Unit] = Route.SimpleRoute(path, title, this)
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

    override def target(resource: UrlResource)(using AssemblerContext): Effect[RoutingTarget] = Effect.const {
      eagerTarget(resource)
    }

    def eagerTarget(resource: UrlResource)(using AssemblerContext): RoutingTarget = {
      val state = pathCodec.decode(resource).getOrElse {
        throw new IllegalStateException(s"Unmatched path ${resource}")
      }
      RoutingTarget(title(state), component(state))
    }

    /** Returns a title for that path. */
    def title(state: State): String

    override def preTitle(resource: UrlResource)(using AssemblerContext): String = eagerTarget(resource).title

    /** Assembles a component for a given path. */
    def component(state: State)(using AssemblerContext): Component
  }

  /** Simple Route without parameters. */
  case class SimpleRoute(
      path: String,
      title: String,
      component: Component
  ) extends EagerRoute {
    override type State = Unit

    val pathCodec: PathCodec[Unit] = PathCodec.const(path)

    override def title(state: Unit): String = title

    override def component(state: Unit)(using AssemblerContext): Component = component
  }

  /** A Route whose target depends on a value. */
  case class DependentRoute[S](
      pathCodec: PathCodec[S],
      fn: S => AssemblerContext ?=> Component,
      titleFn: S => String
  ) extends EagerRoute {

    override type State = S

    override def title(state: S): String = titleFn(state)

    override def component(state: S)(using AssemblerContext): Component = fn(state)
  }

  /** A Lazy route which fetches data. */
  case class LazyRoute[S](
      pathCodec: PathCodec[S],
      eagerTitle: S => String,
      routingTarget: S => AssemblerContext ?=> Effect[RoutingTarget]
  ) extends Route {
    override type State = S

    override def canHandle(resource: UrlResource): Boolean = pathCodec.handles(resource)

    override def target(resource: UrlResource)(using AssemblerContext): Effect[RoutingTarget] = {
      routingTarget(pathCodec.forceDecode(resource))
    }

    override def preTitle(resource: UrlResource)(using AssemblerContext): String = {
      eagerTitle(pathCodec.forceDecode(resource))
    }
  }
}
