package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*


case class TodoItemShower(item: String) extends ComponentBase {
  override def assemble: AssemblyResult = span(item)
}

case class TodoShower(todoList: TodoList) extends ComponentBase {
  override def assemble: AssemblyResult = Assembler[TodoItemShower]
    .mapHtml(x => li(x))
    .seq(ul)
    .contraMap[TodoShower](_.todoList.elements.map(TodoItemShower(_)))
    .mapHtml { inner =>
      div(inner)
    }
    .assemble(this)
}
