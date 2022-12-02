package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoAdder(
    model: Model[TodoList]
)

object TodoAdder extends ComponentDsl {
  implicit val assembler: Assembler[TodoAdder] = (value: TodoAdder) => {
    for {
      textInput <- namedChild("input", TextInput("name"))
      button    <- namedChild("addButton", Button("Add"))
      binding    =
        from(button)(_.clicked)
          .withState(textInput)(_.text)
          .map(_._2)
          .toModel(value.model) { (t, current) =>
            Logger.debug(s"Appending ${t}")
            val result = current.append(t)
            Logger.debug(s"Result ${result}")
            result
          }
    } yield {
      Assembly(
        form(),
        Vector(
          textInput,
          button
        ),
        Vector(
          binding
        )
      )
    }
  }
}
