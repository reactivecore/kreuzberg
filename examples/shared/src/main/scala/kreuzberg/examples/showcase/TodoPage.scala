package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoPage(model: Model[TodoList]) extends ComponentBase {
  override def assemble: AssemblyResult[Unit] = {
    for {
      value  <- subscribe(model)
      shower <- namedChild("shower", TodoShower(value))
      adder  <- namedChild("adder", TodoAdder(model))
    } yield {
      div(
        shower.wrap,
        adder.wrap
      )
    }
  }
}
