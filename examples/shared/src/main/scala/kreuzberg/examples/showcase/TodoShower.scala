package kreuzberg.examples.showcase

import kreuzberg._
import scalatags.Text.all.*

case class TodoItemShower(item: String) extends ComponentBase {
  override def assemble: AssemblyResult = span(item)
}

case class TodoShower(todoList: TodoList) extends ComponentBase {
  override def assemble: AssemblyResult = Assembler[TodoItemShower]
    .mapHtml(li(_))
    .seq(ul)
    .contraMap[TodoShower](_.todoList.elements.map(TodoItemShower(_)))
    .mapHtml { inner =>
      div(inner)
    }
    .assemble(this)
}
