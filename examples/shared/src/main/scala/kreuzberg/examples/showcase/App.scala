package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.extras.{Route, SimpleRouter}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class App()
    extends ComponentBase(
    ) {
  override def assemble: AssemblyResult[Unit] = {
    for {
      model  <- Model.make(
                  "main",
                  TodoList(
                    Seq(
                      "Hello",
                      "World"
                    )
                  )
                )
      menu   <- namedChild("menu", Menu)
      // _            <- subscribe(currentRoute)
      router <- namedChild(
                  "router",
                  SimpleRouter(
                    routes(model),
                    Route.DependentRoute({ case s => NotFoundPage(s) }, _ => "Not Found")
                  )
                )
    } yield {
      Assembly(
        div(),
        Vector(
          menu,
          router
        )
      )
    }
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
