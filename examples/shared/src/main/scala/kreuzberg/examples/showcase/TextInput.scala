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
    ).withRuntime {
      val casted = jsElement[ScalaJsInput]
      new Extractor:
        override def text: String = casted.value
    }
  }

  def change     = jsEvent("change")
  def inputEvent = jsEvent("input")
}

object TextInput {
  trait Extractor {
    def text: String
  }
}
