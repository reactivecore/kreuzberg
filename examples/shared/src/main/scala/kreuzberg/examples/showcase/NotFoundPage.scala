package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class NotFoundPage(path: String) extends ComponentBase {

  override def assemble(using context: AssemblerContext): Assembly = {
    div(
      s"Path ${path} not found"
    )
  }
}
