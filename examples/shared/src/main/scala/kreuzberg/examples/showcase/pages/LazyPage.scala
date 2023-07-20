package kreuzberg.examples.showcase.pages

import kreuzberg.{Html, SimpleComponentBase, SimpleContext}
import kreuzberg.scalatags.all.*
import kreuzberg.scalatags.*

/** Page which is lazy loaded. */
case class LazyPage(result: String) extends SimpleComponentBase {
  override def assemble(using c: SimpleContext): Html = {
    div(s"This page was lazy loaded with this result: ${result}")
  }
}
