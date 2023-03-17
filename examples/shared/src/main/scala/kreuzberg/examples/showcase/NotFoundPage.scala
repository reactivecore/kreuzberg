package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class NotFoundPage(path: String) extends ComponentBase {
  override def assemble: AssemblyResult[Unit] = {
    div(
      s"Path ${path} not found"
    )
  }
}
