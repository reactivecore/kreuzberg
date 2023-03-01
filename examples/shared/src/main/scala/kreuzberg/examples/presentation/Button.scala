package kreuzberg.examples.presentation

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class Button(title: Model[String]) {
  def click = Event.JsEvent("click")
}

object Button extends ComponentDsl {
  given Assembler[Button] with {
    override def assemble(value: Button): AssemblyResult = {
      for {
        titleValue <- subscribe(value.title)
      } yield {
        button(`type` := "button", titleValue)
      }
    }
  }
}