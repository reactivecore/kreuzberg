package kreuzberg.examples.showcase

import kreuzberg._
import scalatags.Text.all.{button, *}

case class Button(caption: String) extends ComponentBase {
  override def assemble: AssemblyResult = button(`type` := "button", caption)

  def clicked = Event.JsEvent("click")
}
