package kreuzberg.examples.showcase

import kreuzberg._
import scalatags.Text.all.*

case class NotFoundPage(path: String) extends ComponentBase {
  override def assemble: AssemblyResult = {
    div(
      s"Path ${path} not found"
    )
  }
}
