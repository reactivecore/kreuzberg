package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoAdder(
    model: Model[TodoList]
) extends ComponentBase {

  val textInput = TextInput("name")
  val button    = Button("Add")
  val onSubmit  = jsEvent("submit", true)

  def assemble(using context: AssemblerContext): Assembly = {
    val sink = EventSink.ModelChange[String, TodoList](
      model,
      { (t, current) =>
        Logger.debug(s"Appending ${t}")
        val result = current.append(t)
        Logger.debug(s"Result ${result}")
        result
      }
    )

    val binding = from(onSubmit).mapUnit
      .or(from(button.clicked).mapUnit)
      .withState(textInput.text)
      .to(sink)

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
