package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

object AboutVersionShower extends ComponentBase {
  def assemble(using context: AssemblerContext): Assembly = {
    span("1.2")
  }
}

object AboutPage extends ComponentBase {

  def assemble(using context: AssemblerContext): Assembly = {
    div("Hello World", AboutVersionShower.wrap)
  }
}
