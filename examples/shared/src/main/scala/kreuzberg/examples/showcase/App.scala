package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.examples.showcase.pages.{
  ExtendedFormPage,
  FormPage,
  IndexPage,
  LazyPage,
  NotFoundPage,
  SubRouterPage,
  TablePage,
  WizzardPage,
  XmlPage
}
import kreuzberg.examples.showcase.todo.{TodoPage, TodoPageWithApi}
import kreuzberg.extras.{PathCodec, Route, RouterSettings, RoutingResult, MainRouter, UrlResource}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/** Sample Application. */
object App extends SimpleComponentBase {

  def assemble(using sc: SimpleContext): Html = {
    div(
      Menu.wrap,
      LoadingIndicator,
      MainRouter(
        routes,
        settings = RouterSettings(
          notFoundHandler = path => RoutingResult("Not Found", NotFoundPage(path)),
          titlePrefix = "Example - "
        )
      )
    )
  }

  private def routes: Vector[Route] = Vector(
    IndexPage,
    TodoPage,
    TodoPageWithApi,
    FormPage,
    ExtendedFormPage,
    WizzardPage,
    XmlPage,
    LazyPage,
    TablePage,
    SubRouterPage,
    Route.SimpleForward("/forward", UrlResource("/"))
  )
}
