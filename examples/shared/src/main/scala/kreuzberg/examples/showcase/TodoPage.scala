package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoPage(model: Model[TodoList]) extends ComponentBase {
  override def assemble: AssemblyResult = {
    for {
      value <- subscribe(model)
      shower = TodoShower(value)
      adder  = TodoAdder(model)
    } yield {
      div(
        shower.wrap,
        adder.wrap
      )
    }
  }
}
