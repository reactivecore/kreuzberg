package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.imperative.{SimpleContext, SimpleComponentBase}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class Label(model: Model[String]) extends SimpleComponentBase {
  override def assemble(implicit c: SimpleContext): Html = {
    val current = subscribe(model)
    span(current)
  }
}

object TriggerPage extends SimpleComponentBase {
  override def assemble(implicit c: SimpleContext): Html = {
    val value    = Model.create("")
    val editor   = TextInput("field")
    val shower   = Label(value)
    val xmlLabel = XmlLabel(value)

    add(
      from(editor.inputEvent)
        .withState(editor)(_.text)
        .changeModel(value)((n, _) => n)
    )
    div(
      "Bitte geben sie was ein: ",
      editor.wrap,
      "Das hier haben sie eingegeben: ",
      shower.wrap,
      br,
      "Und das: ",
      xmlLabel.wrap
    )
  }
}
