package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

object IndexPage extends ComponentBase {
  def assemble(using context: AssemblerContext): Assembly = {
    div(
      h2("Hi There"),
      "Welcome to this small Kreuzberg Demonstration"
    )
  }
}
