package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoAdder(
    model: Model[TodoList]
) extends ComponentBase {

  def assemble: AssemblyResult[Runtime] = {

    val textInput = TextInput("name")
    val button    = Button("Add")
    val binding   = from(button.clicked)
      .withState(textInput)(_.text)
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
