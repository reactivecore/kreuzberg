package kreuzberg.extras

import scalatags.Text.all._
import kreuzberg._
import kreuzberg.scalatags._

case class PlainLink(
    name: String,
    target: String
) extends SimpleComponentBase {
  val onClick = jsEvent("click", true, true)

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
