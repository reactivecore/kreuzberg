package kreuzberg.examples.showcase.components

import kreuzberg.*
import kreuzberg.scalatags.*

case class Button(caption: Subscribeable[String]) extends TemplatingComponentBase {
  def assemble(using sc: SimpleContext): ScalaTagsHtml = {
    button(`type` := "button", caption)
  }

  def onClicked: JsEvent = jsEvent("click")
}
