package kreuzberg.extras

import kreuzberg.*
import kreuzberg.imperative._
import scalatags.Text.all.*

case class PlainLink(
    name: String,
    target: String
) extends SimpleComponentBase {
  val click = Event.JsEvent("click", true, true)

  override def assemble(implicit c: SimpleContext): Html = {
    a(name, href := target)
  }
}

case class RouterLink(
    target: String,
    name: String,
    currentRoute: Model[String],
    routerBus: Bus[String]
) extends SimpleComponentBase {

  override def assemble(implicit c: SimpleContext): Html = {
    val link = anonymousChild(PlainLink(name, target))
    add(
      from(link)(_.click).map(_ => target),
      EventSink.BusCall(routerBus)
    )
    span("[", link, "]")
  }
}
