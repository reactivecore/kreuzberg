package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.extras.RouterLink
import kreuzberg.imperative.{SimpleContext, SimpleComponentBase, PlaceholderTag}
import scalatags.Text.all.*

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
    val items: Seq[PlaceholderTag] = links.map { case (link, name) => anonymousChild(RouterLink(link, name, deco = true)) }
    div(items: _*)
  }
}
