package kreuzberg.examples.showcase.components

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class Button(caption: Subscribeable[String]) extends TemplatingComponentBase {
  def assemble(using context: SimpleContext): ScalaTagsHtml = {
    button(`type` := "button", caption)
  }

  def onClicked: JsEvent = jsEvent("click")
}
