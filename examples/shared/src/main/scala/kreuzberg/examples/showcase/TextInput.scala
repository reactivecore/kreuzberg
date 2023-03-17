package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.dom.ScalaJsInput
import kreuzberg.examples.showcase.TextInput.Extractor
import kreuzberg.imperative.SimpleComponentBaseWithRuntime

case class TextInput(fieldName: String, initialValue: String = "") extends SimpleComponentBaseWithRuntime[Extractor] {

  override def assemble(
      implicit c: SimpleContext
  ): HtmlWithRuntime = {
    html(
      input(name := fieldName, value := initialValue)
    ).withRuntime { provider =>
      val casted = provider.jsElement.asInstanceOf[ScalaJsInput]
      new Extractor:
        override def text: String = casted.value
    }
  }

  def change     = Event.JsEvent("change")
  def inputEvent = Event.JsEvent("input")
}

object TextInput {
  trait Extractor {
    def text: String
  }
}
