package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.extras.RouterLink
import kreuzberg.imperative.{SimpleContext, SimpleComponentBase}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case object Menu extends SimpleComponentBase {

  val links = Seq(
    "/"         -> "Index",
    "/todo"     -> "Todo App",
    "/about"    -> "About",
    "/form"     -> "Form",
    "/wizzard"  -> "Wizzard",
    "/trigger"  -> "Trigger",
    "/notfound" -> "Not Found"
  )

  override def assemble(implicit c: SimpleContext): Html = {
    val items = links.map { case (link, name) => anonymousChild(RouterLink(link, name, deco = true)).wrap }
    div(items: _*)
  }
}
