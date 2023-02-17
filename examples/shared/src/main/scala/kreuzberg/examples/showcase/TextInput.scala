package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.dom.ScalaJsInput

case class TextInput(fieldName: String, initialValue: String = "") extends ComponentBase {
  override def assemble: AssemblyResult = input(name := fieldName, value := initialValue)

  def text: StateGetter[String] = js[ScalaJsInput].get(_.value)

  def change = Event.JsEvent("change")
  def inputEvent = Event.JsEvent("input")
}
