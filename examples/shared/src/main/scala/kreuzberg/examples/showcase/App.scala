package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.examples.showcase.pages.{
  ExtendedFormPage,
  FormPage,
  IndexPage,
  LazyPage,
  NotFoundPage,
  WizzardPage,
  XmlPage
}
import kreuzberg.examples.showcase.todo.{TodoPage, TodoPageWithApi}
import kreuzberg.extras.{PathCodec, Route, SimpleRouter, UrlResource}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

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

  private def routes: Vector[Route] = Vector(
    IndexPage,
    TodoPage,
    TodoPageWithApi,
    FormPage,
    ExtendedFormPage,
    WizzardPage,
    XmlPage,
    LazyPage
  )
}
