package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.extras.{Route, SimpleRouter}
import org.scalajs.dom
import org.scalajs.dom.document
import scalatags.Text.all.*

case class App()
    extends ComponentBase(
    ) {
  override def assemble: AssemblyResult = {
    for {
      model        <- Model.make(
                        "main",
                        TodoList(
                          Seq(
                            "Hello",
                            "World"
                          )
                        )
                      )
      currentRoute <- Model.make("route", "")
      routerBus    <- Bus.make[String]("route")
      menu         <- namedChild("menu", Menu(currentRoute, routerBus))
      // _            <- subscribe(currentRoute)
      router       <- namedChild(
                        "router",
                        SimpleRouter(
                          routes(model),
                          Route.DependentRoute({ case s => NotFoundPage(s) }, _ => "Not Found"),
                          currentRoute,
                          routerBus
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
    Route.SimpleRoute("/form", "Form", FormPage),
    Route.SimpleRoute("/wizzard", "Wizzard", WizzardPage),
    Route.SimpleRoute("/trigger", "Trigger", TriggerPage)
  )
}

object App {
  def main(args: Array[String]): Unit = {
    import kreuzberg._
    val app = App()
    Binder.runOnLoaded(app, "root")
  }
}