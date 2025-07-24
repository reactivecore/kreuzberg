package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.extras.UrlResource
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class NotFoundPage(resource: UrlResource) extends ComponentBase {

  override def assemble(using context: KreuzbergContext): Assembly = {
    div(
      s"Path ${resource} not found"
    )
  }
}
