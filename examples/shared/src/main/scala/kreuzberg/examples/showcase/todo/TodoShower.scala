package kreuzberg.examples.showcase.todo

import kreuzberg.*
import kreuzberg.examples.showcase.todo.{TodoItemShower, TodoList}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoItemShower(item: String) extends SimpleComponentBase {
  override def assemble(using sc: SimpleContext): Html = {
    span(item)
  }
}

case class TodoShower(todoList: Subscribeable[TodoList]) extends TemplatingComponentBase {

  def assemble(using sc: SimpleContext): ScalaTagsHtml = {
    ul(
      todoList.map(_.elements).iter { elem =>
        li(
          TodoItemShower(elem)
        )
      }
    )
  }

  override def update(before: ModelValueProvider): UpdateResult = {
    val valueBefore = before.value(todoList)
    val valueAfter  = read(todoList)
    (for {
      last <- valueAfter.elements.lastOption
      if valueAfter == valueBefore.append(last)
    } yield {
      // Just one element added, lets patch it
      val itemShower = li(TodoItemShower(last))
      UpdateResult.Append(
        Assembly(
          html = itemShower
        )
      )
    }).getOrElse {
      super.update(before)
    }
  }
}
