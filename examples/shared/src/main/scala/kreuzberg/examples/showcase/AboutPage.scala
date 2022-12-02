package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

object AboutVersionShower extends ComponentBase {
  override def assemble: AssemblyResult = span("1.2")
}

object AboutPage extends ComponentBase {

  override def assemble: AssemblyResult = {
    for {
      version <- anonymousChild(AboutVersionShower) // This is used for testing if we garbage collect anonymous children correctly
    } yield {
      Assembly(
        div("Hello World"),
        Vector(version)
      )
    }
  }
}
