package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TextInput(fieldName: String, initialValue: String = "") extends ComponentBase {
  override def assemble: AssemblyResult = input(name := fieldName, value := initialValue)

  def text: StateGetter[String] = js[JsInput].get(_.value)

  def change = Event.JsEvent("change")
  def inputEvent = Event.JsEvent("input")
}
