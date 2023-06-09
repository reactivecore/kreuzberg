package kreuzberg.examples.showcase.components

import kreuzberg.{Html, Model, SimpleComponentBase, SimpleContext}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class Label(model: Model[String]) extends SimpleComponentBase {
  override def assemble(implicit c: SimpleContext): Html = {
    val current = subscribe(model)
    span(current)
  }
}
