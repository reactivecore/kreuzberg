package kreuzberg.examples.showcase.todo

import kreuzberg.*
import kreuzberg.examples.showcase.components.{Button, TextInput}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/** A Simple form which allows to add elements to the todo list. */
case class TodoAdder(
    model: Model[TodoList]
) extends SimpleComponentBase {

  val textInput = TextInput("name")
  val button    = Button("Add")
  val onSubmit  = jsEvent("submit", true)

  def assemble(using sc: SimpleContext): Html = {
    add(
      onSubmit
        .or(button.onClicked)
        .handleAny {
          val entry = textInput.text.read()
          model.update(_.append(entry))
          textInput.text.set("")
        }
    )

    form(
      textInput.wrap,
      button.wrap
    )
  }
}
