package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.dom.{ScalaJsElement, ScalaJsInput}

case class TextInput(fieldName: String, initialValue: String = "") extends SimpleComponentBase {

  override type DomElement = ScalaJsInput

  override def assemble(
      implicit c: SimpleContext
  ): Html = {
    input(name := fieldName, value := initialValue)
  }

  def change     = jsEvent("change")
  def inputEvent = jsEvent("input")
  def text       = jsState(_.value)
}
