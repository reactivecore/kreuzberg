package kreuzberg.examples.showcase.todo

import kreuzberg.*
import kreuzberg.examples.showcase.todo.{TodoAdder, TodoList}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.extras.{Meta, MetaData, SimpleRouted}

object TodoPage extends SimpleComponentBase with SimpleRouted {

  def path  = "/todo"
  def title = "Todo App"

  val model = Model.create(
    TodoList(
      Seq(
        "Hello",
        "World"
      )
    )
  )

  def assemble(using sc: SimpleContext): Html = {
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

  override def metaData: MetaData =
    Seq(
      Meta.property("og:title", "Todo Page!")
    )
}
