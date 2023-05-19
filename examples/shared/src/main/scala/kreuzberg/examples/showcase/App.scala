package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.extras.{Route, SimpleRouter}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class App()
    extends ComponentBase(
    ) {

  val model = Model.create(
    TodoList(
      Seq(
        "Hello",
        "World"
      )
    )
  )

  override def assemble: AssemblyResult[Unit] = {
    Assembly(
      div(
        Menu.wrap,
        SimpleRouter(
          routes(model),
          Route.DependentRoute({ case s => NotFoundPage(s) }, _ => "Not Found")
        )
      )
    )
  }

  private def routes(model: Model[TodoList]) = Vector(
    Route.SimpleRoute("/", "Welcome", IndexPage),
    Route.SimpleRoute("/about", "About", AboutPage),
    Route.SimpleRoute("/todo", "Todo App", TodoPage(model)),
    Route.SimpleRoute("/todoapi", "Todo App (API)", TodoPageWithApi),
    Route.SimpleRoute("/form", "Form", FormPage),
    Route.SimpleRoute("/wizzard", "Wizzard", WizzardPage),
    Route.SimpleRoute("/trigger", "Trigger", TriggerPage)
  )
}
