package kreuzberg.examples.showcase

import kreuzberg._
import scalatags.Text.all.*

object IndexPage extends ComponentBase {
  override def assemble: AssemblyResult = {
    div("Welcome to this cool page")
  }
}
