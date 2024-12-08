package kreuzberg.examples.showcase.pages

import kreuzberg.examples.showcase.SlowApiMock
import kreuzberg.extras.{PathCodec, Route, Routed, RoutingTarget}
import kreuzberg.{Effect, Html, SimpleComponentBase, SimpleContext}
import kreuzberg.scalatags.all.*
import kreuzberg.scalatags.*

import scala.concurrent.duration.*

/** Page which is lazy loaded. */
case class LazyPage(result: String) extends SimpleComponentBase {
  override def assemble(using c: SimpleContext): Html = {
    div(s"This page was lazy loaded with this result: ${result}")
  }
}

object LazyPage extends Routed[String] {
  override def route = Route.LazyRoute(
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
}
