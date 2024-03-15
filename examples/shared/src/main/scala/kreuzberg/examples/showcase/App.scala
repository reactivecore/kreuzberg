package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.examples.showcase.pages.{ExtendedFormPage, FormPage, IndexPage, LazyPage, NotFoundPage, WizzardPage, XmlPage}
import kreuzberg.examples.showcase.todo.{TodoList, TodoPage, TodoPageWithApi}
import kreuzberg.extras.{PathCodec, Route, RoutingTarget, SimpleRouter, UrlResource}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

import scala.concurrent.duration.*
import scala.concurrent.Future

/** Sample Application. */
object App extends SimpleComponentBase {

  def assemble(using context: SimpleContext): Html = {
    div(
      Menu.wrap,
      LoadingIndicator,
      SimpleRouter(
        routes,
        Route.DependentRoute[UrlResource](
          PathCodec.all,
          s => NotFoundPage(s),
          s => "Not Found"
        )
      )
    )
  }

  val lazyPage = Route.LazyRoute(
    PathCodec.prefix("/lazy/"),
    eagerTitle = path => s"Lazy...",
    routingTarget = path => {
      Effect.future {
        SlowApiMock.timer(
          1.second,
          RoutingTarget(
            s"Lazy ${path}",
            LazyPage(path)
          )
        )
      }
    }
  )

  private def routes = Vector(
    Route.SimpleRoute("/", "Welcome", IndexPage),
    Route.SimpleRoute("/todo", "Todo App", TodoPage),
    Route.SimpleRoute("/todoapi", "Todo App (API)", TodoPageWithApi),
    Route.SimpleRoute("/form", "Form", FormPage),
    Route.SimpleRoute("/form2", "Extended Form", ExtendedFormPage),
    Route.SimpleRoute("/wizzard", "Wizzard", WizzardPage),
    Route.SimpleRoute("/xml", "XML", XmlPage),
    lazyPage
  )
}
