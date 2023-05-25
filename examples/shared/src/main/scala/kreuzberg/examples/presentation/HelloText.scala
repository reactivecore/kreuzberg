package kreuzberg.examples.presentation

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.dom.{ScalaJsElement, ScalaJsInput}

case class Label(model: Model[String]) extends SimpleComponentBase {
  def assemble(implicit c: SimpleContext): Html = {
    val value = subscribe(model)
    div(value)
  }
}

case class InputField(fieldName: String) extends SimpleComponentBase {

  override type DomElement = ScalaJsInput

  def assemble(implicit c: SimpleContext): Html = {
    html(input(name := fieldName))
  }

  def text = jsState(_.value)

  def inputEvent = jsEvent("input")
}

object Shower extends SimpleComponentBase {
  def assemble(implicit c: SimpleContext): Html = {
    val text       = Model.create("")
    val inputField = InputField("text")
    add(
      from(inputField.inputEvent)
        .withState(inputField.text)
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
