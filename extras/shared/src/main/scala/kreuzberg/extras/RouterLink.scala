package kreuzberg.extras

import scalatags.Text.all.*
import kreuzberg.*
import kreuzberg.scalatags.*
import org.scalajs.dom.Event

case class PlainLink(
    name: String,
    target: String
) extends SimpleComponentBase {

  val onClick: EventSource[Event] = jsEvent("click", true).hook(_.preventDefault())

  override def assemble(implicit c: SimpleContext): Html = {
    a(name, href := target)
  }
}

case class RouterLink(
    target: String,
    name: String,
    deco: Boolean = false
) extends SimpleComponentBase {

  override def assemble(implicit c: SimpleContext): Html = {
    val link = PlainLink(name, target)
    add(
      link.onClick.handleAny {
        SimpleRouter.gotoTarget(UrlResource(target))
      }
    )
    if (deco) {
      span("[", link.wrap, "]")
    } else {
      span(link.wrap)
    }
  }
}
