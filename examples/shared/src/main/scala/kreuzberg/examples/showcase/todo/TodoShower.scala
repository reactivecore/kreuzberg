package kreuzberg.examples.showcase.todo

import kreuzberg.*
import kreuzberg.examples.showcase.todo.{TodoItemShower, TodoList}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoItemShower(item: String) extends SimpleComponentBase {
  override def assemble(using context: SimpleContext): Html = {
    span(item)
  }
}

case class TodoShower(todoList: TodoList) extends SimpleComponentBase {

  def assemble(implicit c: SimpleContext): Html = {
    val parts = todoList.elements.map { element =>
      li(
        TodoItemShower(element).wrap
      )
    }
    ul(parts)
  }
}
