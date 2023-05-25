package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

object AboutVersionShower extends ComponentBase {
  override def assemble: AssemblyResult = span("1.2")
}

object AboutPage extends ComponentBase {

  override def assemble: AssemblyResult = {
    Assembly(
      div("Hello World", AboutVersionShower.wrap)
    )
  }
}
