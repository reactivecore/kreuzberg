package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

object IndexPage extends ComponentBase {
  override def assemble: AssemblyResult[Unit] = {
    div("Welcome to this cool page")
  }
}
