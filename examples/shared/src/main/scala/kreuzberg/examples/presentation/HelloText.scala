package kreuzberg.examples.presentation

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.dom.{ScalaJsElement, ScalaJsInput}
import kreuzberg.imperative.SimpleComponentBaseWithRuntime

case class Label(model: Model[String]) extends SimpleComponentBase {
  def assemble(implicit c: SimpleContext): Html = {
    val value = subscribe(model)
    div(value)
  }
}

case class InputField(fieldName: String) extends SimpleComponentBaseWithRuntime[InputField.Rep] {
  def assemble(implicit c: SimpleContext): HtmlWithRuntime = {
    html(input(name := fieldName)).withRuntime {
      val casted = jsElement[ScalaJsInput]
      new InputField.Rep:
        override def text: String = casted.value
    }
  }

  def inputEvent = Event.JsEvent("input")
}

object InputField {
  trait Rep {
    def text: String
  }
}

object Shower extends SimpleComponentBase {
  def assemble(implicit c: SimpleContext): Html = {
    val text       = Model.create("")
    val inputField = anonymousChild(InputField("text"))
    add(
      from(inputField)(_.inputEvent)
        .withState(inputField)(_.text)
        .intoModel(text)
    )
    div(
      "Enter Something: ",
      inputField.wrap,
      "You entered: ",
      Label(text).wrap
    )
  }
}
