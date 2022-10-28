package kreuzberg.extras

import kreuzberg.*
import kreuzberg.imperative._
import scalatags.Text.all.*

case class PlainLink(
    name: String,
    target: String
) extends ImperativeComponentBase2 {
  val click = Event.JsEvent("click", true, true)

  override def assemble2(implicit c: AssemblyContext2): Html = {
    a(name, href := target)
  }
}

case class RouterLink(
    target: String,
    name: String,
    currentRoute: Model[String],
    routerBus: Bus[String]
) extends ImperativeComponentBase2 {

  override def assemble2(implicit c: AssemblyContext2): Html = {
    val link = anonymousChild(PlainLink(name, target))
    add(
      from(link)(_.click).map(_ => target),
      EventSink.BusCall(routerBus)
    )
    span("[", link, "]")
  }
}
