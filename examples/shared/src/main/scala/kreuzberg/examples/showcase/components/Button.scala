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

case class ReactiveButton(caption: Subscribeable[String]) extends SimpleComponentBase {
  def assemble(using context: SimpleContext): Html = {
    button(`type` := "button", caption.subscribe())
  }

  def onClicked = jsEvent("click")
}
