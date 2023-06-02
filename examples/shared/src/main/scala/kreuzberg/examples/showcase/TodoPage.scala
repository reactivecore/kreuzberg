package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoPage(model: Model[TodoList]) extends ComponentBase {
  def assemble(using context: AssemblerContext): Assembly = {
    val value  = read(model)
    val shower = TodoShower(value)
    val adder  = TodoAdder(model)

    Assembly(
      div(
        shower.wrap,
        adder.wrap
      ),
      subscriptions = Vector(model)
    )
  }
}
