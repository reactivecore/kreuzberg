package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.examples.showcase.pages.{FormPage, IndexPage, NotFoundPage, XmlPage, WizzardPage}
import kreuzberg.examples.showcase.todo.{TodoList, TodoPage, TodoPageWithApi}
import kreuzberg.extras.{Route, SimpleRouter}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/** Sample Application. */
object App extends SimpleComponentBase {

  def assemble(using context: SimpleContext): Html = {
    div(
      Menu.wrap,
      SimpleRouter(
        routes,
        Route.DependentRoute({ case s => NotFoundPage(s) }, _ => "Not Found")
      )
    )
  }

  private def routes = Vector(
    Route.SimpleRoute("/", "Welcome", IndexPage),
    Route.SimpleRoute("/todo", "Todo App", TodoPage),
    Route.SimpleRoute("/todoapi", "Todo App (API)", TodoPageWithApi),
    Route.SimpleRoute("/form", "Form", FormPage),
    Route.SimpleRoute("/wizzard", "Wizzard", WizzardPage),
    Route.SimpleRoute("/xml", "XML", XmlPage)
  )
}
