package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoAdder(
    model: Model[TodoList]
) extends ComponentBase {

  def assemble: AssemblyResult[Runtime] = {
    for {
      textInput <- namedChild("input", TextInput("name"))
      button    <- namedChild("addButton", Button("Add"))
      binding    =
        from(button)(_.clicked)
          .withState(textInput)(_.text)
          .changeModel(model) { (t, current) =>
            Logger.debug(s"Appending ${t}")
            val result = current.append(t)
            Logger.debug(s"Result ${result}")
            result
          }
    } yield {
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
}
