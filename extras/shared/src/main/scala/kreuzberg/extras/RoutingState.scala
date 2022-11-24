package kreuzberg.extras

import kreuzberg.{AssemblyState, Model, Provider}
import kreuzberg.util.Stateful

case class RoutingState(
    currentRoute: Model[String]
)

object RoutingState {
  given routingStateProvider: Provider[RoutingState] with {
    override def provide: Stateful[AssemblyState, RoutingState] = {
      for {
        route <- Model.makeRoot("routing.route", BrowserRouting.getCurrentPath())
      } yield {
        RoutingState(route)
      }
    }
  }
}
