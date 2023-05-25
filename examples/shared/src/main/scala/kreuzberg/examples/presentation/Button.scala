package kreuzberg.examples.presentation

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class Button(title: Model[String]) extends ComponentBase {
  def click = jsEvent("click")

  def assemble: AssemblyResult = {
    for {
      titleValue <- subscribe(title)
    } yield {
      button(`type` := "button", titleValue)
    }
  }
}
