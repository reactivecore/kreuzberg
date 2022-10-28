package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.extras.RouterLink
import kreuzberg.imperative.{AssemblyContext2, ImperativeComponentBase2, PlaceholderTag}
import scalatags.Text.all.*

case class Menu(currentRoute: Model[String], routerBus: Bus[String]) extends ImperativeComponentBase2 {

  val links = Seq(
    "/"         -> "Index",
    "/todo"     -> "Todo App",
    "/about"    -> "About",
    "/form"     -> "Form",
    "/wizzard"  -> "Wizzard",
    "/trigger"  -> "Trigger",
    "/notfound" -> "Not Found"
  )

  override def assemble2(implicit c: AssemblyContext2): Html = {
    val items: Seq[PlaceholderTag] = links.map { case (link, name) => anonymousChild(RouterLink(link, name, currentRoute, routerBus)) }
    div(items: _*)
  }
}
