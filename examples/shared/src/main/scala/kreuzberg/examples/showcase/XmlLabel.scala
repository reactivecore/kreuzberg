package kreuzberg.examples.showcase

import kreuzberg.{Html, Model, SimpleComponentBase, SimpleContext}
import kreuzberg.xml._

/** Demonstrates the use of Scala XML. */
case class XmlLabel(model: Model[String]) extends SimpleComponentBase {
  override def assemble(implicit c: SimpleContext): Html = {
    val current = subscribe(model)
    <span>Hello {current} in XML</span>
  }
}
