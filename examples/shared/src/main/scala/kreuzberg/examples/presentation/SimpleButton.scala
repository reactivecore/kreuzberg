package kreuzberg.examples.presentation

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.imperative.*

case class SimpleButton(title: Model[String]) extends SimpleComponentBase {
  def assemble(implicit c: SimpleContext): Html = {
    val titleValue = subscribe(title)
    button(`type` := "button", titleValue)
  }
}
