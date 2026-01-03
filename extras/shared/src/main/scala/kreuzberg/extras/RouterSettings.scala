package kreuzberg.extras

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class RouterSettings(
    notFoundHandler: UrlResource => RoutingResult = RouterSettings.DefaultNotFoundHandler(_),
    titlePrefix: String = "",
    errorHandler: (UrlResource, Error) => RoutingResult = RouterSettings.DefaultErrorHandler(_, _),
    loadingHandler: UrlResource => Component = RouterSettings.DefaultLoadingHandler(_)
)

object RouterSettings {

  /** Default Error Handler. */
  class DefaultErrorHandler(url: UrlResource, error: Error) extends SimpleComponentBase with Page {

    override def title: String = "Error"

    def assemble(using sc: SimpleContext): Html = {
      div(
        h2("Error"),
        s"An unrecoverable error handled on loading route ${url}",
        ul(
          error.asList.map(msg => li(msg))
        )
      )
    }
  }

  class DefaultNotFoundHandler(url: UrlResource) extends SimpleComponentBase with Page {

    override def title: String = "Not Found"

    override def assemble(using simpleContext: SimpleContext): Html = {
      div(
        h2("Not Found"),
        s"Resource ${url} could not be found"
      )
    }
  }

  class DefaultLoadingHandler(url: UrlResource) extends SimpleComponentBase {
    def assemble(using sc: SimpleContext): Html = {
      div("Loading...")
    }
  }
}
