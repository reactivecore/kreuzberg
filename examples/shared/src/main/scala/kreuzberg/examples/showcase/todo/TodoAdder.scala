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

  def assemble(using context: SimpleContext): Html = {
    val sink = EventSink.ModelChange[String, TodoList](
      model,
      { (t, current) =>
        Logger.debug(s"Appending ${t}")
        val result = current.append(t)
        Logger.debug(s"Result ${result}")
        result
      }
    )

    add(
      onSubmit
        .or(button.onClicked)
        .withState(textInput.text)
        .changeModel(model) { (entry, current) => current.append(entry) }
        .and
        .map { _ => "" }
        .intoProperty(textInput.text)
    )

    form(
      textInput.wrap,
      button.wrap
    )
  }
}
