package kreuzberg.examples.showcase.components

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class Button(caption: String) extends ComponentBase {
  def assemble(using context: AssemblerContext): Assembly = {
    button(`type` := "button", caption)
  }

  def onClicked = jsEvent("click")
}
