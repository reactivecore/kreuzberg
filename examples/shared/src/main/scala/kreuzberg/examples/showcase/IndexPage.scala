package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

object IndexPage extends ComponentBase {
  def assemble(using context: AssemblerContext): Assembly = {
    div("Welcome to this cool page")
  }
}
