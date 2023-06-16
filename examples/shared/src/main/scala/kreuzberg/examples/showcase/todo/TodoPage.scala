package kreuzberg.examples.showcase.todo

import kreuzberg.*
import kreuzberg.examples.showcase.todo.{TodoAdder, TodoList}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

object TodoPage extends SimpleComponentBase {

  val model = Model.create(
    TodoList(
      Seq(
        "Hello",
        "World"
      )
    )
  )

  def assemble(using context: SimpleContext): Html = {
    val shower = TodoShower(model)
    val adder  = TodoAdder(model)

    div(
      h2("Client Side TODO App"),
      div(
        "This example shows the use of Models to mutate a state on client side"
      ),
      div(
        shower.wrap,
        adder.wrap
      )
    )
  }
}
