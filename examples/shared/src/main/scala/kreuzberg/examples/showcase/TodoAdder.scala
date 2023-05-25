package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoAdder(
    model: Model[TodoList]
) extends ComponentBase {

  val textInput = TextInput("name")
  val button    = Button("Add")

  def assemble: AssemblyResult = {
    val binding = from(button.clicked)
      .withState(textInput.text)
      .changeModel(model) { (t, current) =>
        Logger.debug(s"Appending ${t}")
        val result = current.append(t)
        Logger.debug(s"Result ${result}")
        result
      }
    Assembly(
      form(
        textInput.wrap,
        button.wrap
      ),
      Vector(
        binding
      )
    )
  }
}
