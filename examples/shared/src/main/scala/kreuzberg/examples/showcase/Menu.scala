package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.extras.{RouterLink, SimpleRouter}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case object Menu extends SimpleComponentBase {

  val links = Seq(
    "/"         -> "Index",
    "/todo"     -> "Todo App",
    "/todoapi"  -> "Todo App API",
    "/form"     -> "Form",
    "/wizzard"  -> "Wizzard",
    "/xml"      -> "XML",
    "/notfound" -> "Not Found",
    "/lazy/123" -> "Lazy"
  )

  override def assemble(implicit c: SimpleContext): Html = {
    val items = links.map { case (link, name) => RouterLink(link, name, deco = true).wrap }
    div(items: _*)
  }
}

