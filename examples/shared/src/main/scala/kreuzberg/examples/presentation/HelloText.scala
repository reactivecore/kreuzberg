package kreuzberg.examples.presentation

import kreuzberg.*
import kreuzberg.imperative.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.examples.showcase.JsInput

case class Label(model: Model[String]) extends SimpleComponentBase {
  def assemble(implicit c: SimpleContext): Html = {
    val value = subscribe(model)
    div(value)
  }
}

case class InputField(fieldName: String) extends SimpleComponentBase {
  def assemble(implicit c: SimpleContext): Html = {
    input(name := fieldName)
  }

  def inputEvent = Event.JsEvent("input")

  def text: StateGetter[String] = js[JsInput].get(_.value)
}

object Shower extends SimpleComponentBase {
  def assemble(implicit c: SimpleContext): Html = {
    val text       = model("text", defaultValue = "")
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
