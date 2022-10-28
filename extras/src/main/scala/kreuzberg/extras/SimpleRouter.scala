package kreuzberg.extras

import kreuzberg.*
import kreuzberg.util.Stateful
import scalatags.Text.all.*

trait Route {
  def canHandle(path: String): Boolean

  def title(path: String): String

  def node(id: ComponentId, path: String): Stateful[AssemblyState, Node]
}

object Route {

  /** Simple Route without parameters. */
  case class SimpleRoute[T](
      path: String,
      title: String,
      component: T
  )(implicit assembler: Assembler[T])
      extends Route {
    override def canHandle(path: String): Boolean = {
      this.path == path
    }

    override def title(path: String): String = title

    override def node(id: ComponentId, path: String): Stateful[AssemblyState, Node] = {
      assembler.assembleWithId(id, component)
    }
  }

  case class DependentRoute[T](
      fn: PartialFunction[String, T],
      titleFn: String => String
  )(implicit assembler: Assembler[T])
      extends Route {
    override def canHandle(path: String): Boolean = fn.isDefinedAt(path)

    override def title(path: String): String = titleFn(path)

    override def node(id: ComponentId, path: String): Stateful[AssemblyState, Node] =
      assembler.assembleWithId(id, fn(path))
  }
}

case class SimpleRouter(
    routes: Vector[Route],
    notFoundRoute: Route,
    currentRoute: Model[String],
    routerBus: Bus[String]
) extends ComponentBase {
  override def assemble: AssemblyResult = {
    for {
      routeValue <- subscribe(currentRoute)
      _           = println(s"Rendering SimpleRouter with value ${routeValue}")
      id         <- Stateful[AssemblyState, ComponentId](_.ensureChildren(routeValue))
      route       = decideRoute(routeValue)
      assembled  <- route.node(id, routeValue)
      binding     = EventBinding(
                      EventSource.WindowJsEvent(Event.JsEvent("load")),
                      EventSink
                        .ModelChange(
                          currentRoute,
                          (_, m) => {
                            val path = org.scalajs.dom.window.location.pathname
                            println(s"Load Event: ${path}")
                            path
                          }
                        )
                        .and(EventSink.Custom { _ =>
                          val path  = org.scalajs.dom.window.location.pathname
                          val title = decideRoute(path).title(path)
                          org.scalajs.dom.document.title = title
                        })
                    )
      binding2    = EventBinding(
                      EventSource.BusEvent(routerBus),
                      EventSink
                        .ModelChange[String, String](
                          currentRoute,
                          (e, _) => e
                        )
                        .and(
                          EventSink.Custom[String] { target =>
                            val title = decideRoute(target).title(target)
                            // println(s"Decided title ${target} is ${title}")
                            org.scalajs.dom.window.history.pushState((), title, target)
                            org.scalajs.dom.document.title = title
                          }
                        )
                    )
      binding3    = EventBinding(
                      EventSource.WindowJsEvent(Event.JsEvent("popstate")),
                      EventSink.Custom { event =>
                        val path  = org.scalajs.dom.window.location.pathname
                        println(s"Popstate event ${path}")
                        val title = decideRoute(path).title(path)
                        org.scalajs.dom.document.title = title
                      }
                    )
    } yield {
      Assembly(
        div(),
        Vector(assembled),
        Vector(
          binding,
          binding2,
          binding3
        )
      )
    }
  }

  private def decideRoute(path: String): Route = {
    routes.find(_.canHandle(path)).getOrElse(notFoundRoute)
  }
}
