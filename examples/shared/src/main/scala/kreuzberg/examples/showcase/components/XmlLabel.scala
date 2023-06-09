package kreuzberg.examples.showcase.components

import kreuzberg.xml.*
import kreuzberg.{Html, Model, SimpleComponentBase, SimpleContext}

/** Demonstrates the use of Scala XML. */
case class XmlLabel(model: Model[String]) extends SimpleComponentBase {
  override def assemble(implicit c: SimpleContext): Html = {
    val current = subscribe(model)
    <span>{current}</span>
  }
}
