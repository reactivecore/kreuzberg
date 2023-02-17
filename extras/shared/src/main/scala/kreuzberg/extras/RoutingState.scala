package kreuzberg.extras

import kreuzberg.imperative.{AssemblyContext, ImperativeDsl}
import kreuzberg.{AssemblyState, EventSink, Model, Provider}
import kreuzberg.util.Stateful

case class RoutingState(
    currentRoute: String
)

object RoutingState {

  /** Generates an Event sink which goes to a specific URL (imperative) */
  def goto[E](route: String)(implicit c: AssemblyContext): EventSink.ModelChange[E, RoutingState] = {
    val state = c.transformFn(_.provide[Model[RoutingState]])
    EventSink.ModelChange(
      state,
      (_, m) => m.copy(currentRoute = route)
    )
  }

  given routingStateProvider: Provider[Model[RoutingState]] with {
    override def provide: Stateful[AssemblyState, Model[RoutingState]] = {
      Model.makeRoot("routing.route", RoutingState(BrowserRouting.getCurrentPath()))
    }
  }
}
