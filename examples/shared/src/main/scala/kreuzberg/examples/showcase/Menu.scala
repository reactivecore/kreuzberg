package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.examples.showcase.pages.{LazyPage, IndexPage}
import kreuzberg.extras.RouterLink
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case object Menu extends SimpleComponentBase {

  val links = Seq(
    IndexPage.url.str             -> "Index",
    "/todo"                       -> "Todo App",
    "/todoapi"                    -> "Todo App API",
    "/form"                       -> "Form",
    "/form2"                      -> "Extended Form",
    "/wizzard"                    -> "Wizzard",
    "/xml"                        -> "XML",
    "/notfound"                   -> "Not Found",
    "/table"                      -> "Table",
    LazyPage.route.url("123").str -> "Lazy"
  )

  override def assemble(implicit c: SimpleContext): Html = {
    val items = links.map { case (link, name) => RouterLink(link, name, deco = true).wrap }
    div(items*)
  }
}
