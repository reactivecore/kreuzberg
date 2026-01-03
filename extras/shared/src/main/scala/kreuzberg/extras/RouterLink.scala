package kreuzberg.extras

import scalatags.Text.all.*
import kreuzberg.*
import kreuzberg.scalatags.*
import org.scalajs.dom.Event

case class PlainLink(
    name: String,
    target: String
) extends SimpleComponentBase {

  val onClick: EventSource[Event] = jsEvent("click", true, true)

  override def assemble(using sc: SimpleContext): Html = {
    a(name, href := target)
  }
}

case class RouterLink(
    target: UrlResource,
    name: String,
    deco: Boolean = false
) extends SimpleComponentBase {

  override def assemble(using sc: SimpleContext): Html = {
    val link = PlainLink(name, target.str)
    add(
      link.onClick.handleAny {
        Router.goto(target)
      }
    )
    if (deco) {
      span("[", link.wrap, "]")
    } else {
      span(link.wrap)
    }
  }
}
