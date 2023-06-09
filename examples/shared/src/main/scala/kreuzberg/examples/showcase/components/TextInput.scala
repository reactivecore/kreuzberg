package kreuzberg.examples.showcase.components

import kreuzberg.*
import kreuzberg.dom.{ScalaJsElement, ScalaJsInput}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TextInput(fieldName: String, initialValue: String = "") extends SimpleComponentBase {

  override type DomElement = ScalaJsInput

  override def assemble(
      implicit c: SimpleContext
  ): Html = {
    input(name := fieldName, value := initialValue)
  }

  def onChange     = jsEvent("change")
  def onInputEvent = jsEvent("input")
  def text       = jsState(_.value)
}
