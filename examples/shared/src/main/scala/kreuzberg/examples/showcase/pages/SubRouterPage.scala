package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.extras.{
  Page,
  PathCodec,
  Renderer,
  Route,
  Routed,
  Router,
  RouterLink,
  RouterSettings,
  RoutingResult,
  SubRouter,
  UrlPath
}

object SubRouterPage extends SimpleComponentBase with Page with Routed {

  override def title: String = "SubRouter"

  object Page1 extends SimpleComponentBase with Page {
    override def title: String = "Page1"

    override def assemble(using simpleContext: SimpleContext): Html = {
      div("Page1")
    }
  }

  object Page2 extends SimpleComponentBase with Page {
    override def title: String = "Page2"

    override def assemble(using simpleContext: SimpleContext): Html = {
      div("Page2")
    }
  }

  val basePath = PathCodec.RecursivePath.Start("/subrouter")
  val page1    = Route.SimpleRoute("page1", Page1)
  val page2    = Route.SimpleRoute("page2", Page2)
  val root     = Route.SimpleForward(UrlPath(), page1.url)

  val subRouter = SubRouter(
    "/subrouter",
    routes = Vector(
      root,
      page1,
      page2
    ),
    settings = RouterSettings(
      titlePrefix = "SubRouter - "
    )
  )

  override val route = subRouter.makeSimpleRoute(this)

  override def assemble(using simpleContext: SimpleContext): Html = {
    div(
      subRouter,
      ul(
        li(RouterLink(subRouter.resolve(page1.url), "Page 1")),
        li(RouterLink(subRouter.resolve(page2.url), "Page 2"))
      ),
      div(
        "Current Path",
        Renderer(Router.currentUrl) { url =>
          span(url.toString)
        }
      )
    )
  }
}
