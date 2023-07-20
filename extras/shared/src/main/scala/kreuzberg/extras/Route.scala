package kreuzberg.extras

import kreuzberg._

/** A Route within [[SimpleRouter]] */
trait Route {

  /** Returns true if the route can handle a given path. */
  def canHandle(path: String): Boolean

  /** Title displayed while loading. */
  def preTitle(path: String): String

  /** Execute the route, can load lazy. */
  def target(path: String)(using AssemblerContext): Effect[RoutingTarget]
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

    override def canHandle(path: String): Boolean = pathCodec.handles(path)

    override def target(path: String)(using AssemblerContext): Effect[RoutingTarget] = Effect.const {
      eagerTarget(path)
    }

    def eagerTarget(path: String): RoutingTarget = {
      val state = pathCodec.decode(path).getOrElse {
        throw new IllegalStateException(s"Unmatched path ${path}")
      }
      RoutingTarget(title(state), component(state))
    }

    /** Returns a title for that path. */
    def title(state: State): String

    override def preTitle(path: String): String = eagerTarget(path).title

    /** Assembles a component for a given path. */
    def component(state: State): Component
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

    override def component(state: Unit): Component = component
  }

  /** A Route whose target depends on a value. */
  case class DependentRoute[S](
      pathCodec: PathCodec[S],
      fn: S => Component,
      titleFn: S => String
  ) extends EagerRoute {

    override type State = S

    override def title(state: S): String = titleFn(state)

    override def component(state: S): Component = fn(state)
  }

  /** A Lazy route which fetches data. */
  case class LazyRoute[S](
      pathCodec: PathCodec[S],
      eagerTitle: S => String,
      routingTarget: S => AssemblerContext ?=> Effect[RoutingTarget]
  ) extends Route {

    override def canHandle(path: String): Boolean = pathCodec.handles(path)

    override def target(path: String)(using AssemblerContext): Effect[RoutingTarget] = {
      routingTarget(pathCodec.forceDecode(path))
    }

    override def preTitle(path: String): String = {
      eagerTitle(pathCodec.forceDecode(path))
    }
  }
}
