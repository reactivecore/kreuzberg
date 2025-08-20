package kreuzberg.examples.showcase.components

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import org.scalajs.dom.html.Input

case class TextInput(fieldName: String, initialValue: String = "") extends SimpleComponentBase {

  override type DomElement = Input

  override def assemble(using sc: SimpleContext): Html = {
    input(name := fieldName, value := initialValue)
  }

  def onChange     = jsEvent("change")
  def onInputEvent = jsEvent("input")
  def text         = jsProperty(_.value, (r, v) => r.value = v)
}
