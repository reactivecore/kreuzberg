package kreuzberg.examples.showcase.components

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class Label(model: Subscribeable[String]) extends SimpleComponentBase {
  override def assemble(implicit c: SimpleContext): Html = {
    val current = subscribe(model)
    span(current)
  }
}
