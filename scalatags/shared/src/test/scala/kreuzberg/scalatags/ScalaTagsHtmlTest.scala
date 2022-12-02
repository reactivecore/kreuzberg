package kreuzberg.scalatags
import kreuzberg.{Assembler, ComponentId, Html}
import kreuzberg.imperative.{SimpleComponentBase, SimpleContext}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

class ScalaTagsHtmlTest extends TestBase {
  val simple = ScalaTagsHtml(
    div("Hello World", span("How are you?"))
  )
  it should "work in a simple example" in {
    simple.renderToString() shouldBe """<div>Hello World<span>How are you?</span></div>"""
    simple
      .addInner(
        Seq(
          ScalaTagsHtml(div("Foo")),
          ScalaTagsHtml(div("Bar"))
        )
      )
      .renderToString() shouldBe """<div>Hello World<span>How are you?</span><div>Foo</div><div>Bar</div></div>"""

    simple
      .withId(ComponentId(123))
      .renderToString() shouldBe """<div data-id="123">Hello World<span>How are you?</span></div>"""
  }

  object Foo extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      span("Boom!")
    }
  }

  object Bar extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      div(
        "Hello World",
        Foo.wrap
      )
    }
  }

  it should "support placeholders" in {
    Assembler
      .single(Bar)
      .renderWithId(ComponentId(0))
      .renderToString() shouldBe """<div data-id="0">Hello World<span data-id="1">Boom!</span></div>"""
  }

}
