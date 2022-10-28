package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.imperative.{AssemblyContext2, ImperativeComponentBase2}
import scalatags.Text.all.*

case class Label(model: Model[String]) extends ImperativeComponentBase2 {
  override def assemble2(implicit c: AssemblyContext2): Html = {
    val current = subscribe(model)
    span(current)
  }
}

object TriggerPage extends ImperativeComponentBase2 {
  override def assemble2(implicit c: AssemblyContext2): Html = {
    val value  = model("value", "")
    val editor = child("editor", TextInput("field"))
    val shower = child("shower", Label(value))

    add(
      from(editor)(_.inputEvent)
        .withReplacedState(editor)(_.text)
        .toModel(value)((n, _) => n)
    )
    div(
      "Bitte geben sie was ein: ",
      editor,
      "Das hier haben sie eingegeben: ",
      shower
    )
  }
}
