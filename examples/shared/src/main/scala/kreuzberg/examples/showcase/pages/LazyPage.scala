package kreuzberg.examples.showcase.pages

import kreuzberg.examples.showcase.SlowApiMock
import kreuzberg.extras.{PathCodec, Route, RouteWithCodec, Routed, RoutingResult}
import kreuzberg.{Html, SimpleComponentBase, SimpleContext}
import kreuzberg.scalatags.all.*
import kreuzberg.scalatags.*

import scala.concurrent.duration.*

/** Page which is lazy loaded. */
case class LazyPage(result: String) extends SimpleComponentBase {
  def assemble(using sc: SimpleContext): Html = {
    div(s"This page was lazy loaded with this result: ${result}")
  }
}

object LazyPage extends Routed {
  override def route: RouteWithCodec[String] = Route.LazyRoute(
    PathCodec.recursive("/lazy").string.one,
    eagerTitle = _ => "Lazy...",
    fn = path => {
      SlowApiMock.timer(
        1.second,
        RoutingResult(
          s"Lazy ${path}",
          LazyPage(path)
        )
      )
    }
  )
}
