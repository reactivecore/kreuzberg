package kreuzberg.extras

import kreuzberg._

/** A Route within [[SimpleRouter]] */
trait Route {

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

object Route {

  /** A Route which directly translates a path into a component */
  trait EagerRoute extends Route {
    type State
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

    override def canHandle(resource: UrlResource): Boolean = pathCodec.handles(resource)

    override def target(resource: UrlResource)(using AssemblerContext): Effect[RoutingTarget] = {
      routingTarget(pathCodec.forceDecode(resource))
    }

    override def preTitle(resource: UrlResource)(using AssemblerContext): String = {
      eagerTitle(pathCodec.forceDecode(resource))
    }
  }
}
