package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.util.Stateful

case class TodoItemShower(item: String) extends ComponentBase {
  override def assemble: AssemblyResult[Unit] = span(item)
}

case class TodoShower(todoList: TodoList) extends SimpleComponentBase {

  def assemble(implicit c: SimpleContext): Html = {
    val parts = todoList.elements.map { element =>
      div(
        TodoItemShower(element).wrap
      )
    }
    div(parts)
  }
}
