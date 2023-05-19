package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*


case class Button(caption: String) extends ComponentBase {
  override def assemble: AssemblyResult[Unit] = button(`type` := "button", caption)

  def clicked = jsEvent("click")
}
