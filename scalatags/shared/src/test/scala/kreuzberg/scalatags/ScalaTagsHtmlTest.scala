package kreuzberg.scalatags
import kreuzberg.{Html, Identifier, SimpleComponentBase, SimpleContext, SimpleHtml}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.engine.common.Assembler
import kreuzberg.testcore.TestBase

class ScalaTagsHtmlTest extends TestBase {
  val simple = ScalaTagsHtml(
    div("Hello World", span("How are you?"))
  )
  it should "work in a simple example" in {
    val flat     = simple.flat()
    val expected = """<div>Hello World<span>How are you?</span></div>"""
    flat.renderWithoutPlaceholders() shouldBe expected
    simple.renderToString() shouldBe expected

    simple
      .withId(Identifier(123))
      .renderToString() shouldBe """<div data-id="123">Hello World<span>How are you?</span></div>"""
  }

  it should "like comments" in {
    val c = simple.addComment("Boom").addComment("Bu--zz")
    c.renderToString() shouldBe """<div><!-- Buzz --><!-- Boom -->Hello World<span>How are you?</span></div>"""
  }

  case class Foo() extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      span("Boom!")
    }
  }

  case class Bar() extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      div(
        "Hello World",
        Foo().wrap
      )
    }
  }

  it should "support placeholders" in {
    Assembler
      .single(() => Bar())
      .html
      .renderToString() shouldBe """<div>Hello World<component id="2"/></div>"""
  }

  case class Nested() extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      val a = Foo().wrap
      val b = Foo().wrap
      div(
        a,
        p(
          ScalaTagsHtmlEmbedding(
            div(
              b
            )
          )
        )
      )
    }
  }

  it should "work nested with transformation" in {
    val singleRendered = Assembler
      .single(() => Nested())
      .html
      .renderToString()

    singleRendered shouldBe """<div><component id="2"/><p><div><component id="3"/></div></p></div>"""

    val rendered = Assembler
      .singleTree(() => Nested())
      .render()
    rendered shouldBe """<div data-id="1"><!-- Nested --><span data-id="2"><!-- Foo -->Boom!</span><p><div><span data-id="3"><!-- Foo -->Boom!</span></div></p></div>"""
  }

  it should "append and prepend" in {
    val html = ScalaTagsHtml(div("Hello World"))
    html
      .appendChild(
        ScalaTagsHtml(h1("Boom"))
      )
      .prependChild(
        ScalaTagsHtml(h2("Bar"))
      )
      .toString shouldBe "<div><h2>Bar</h2>Hello World<h1>Boom</h1></div>"
    html
      .appendChild(SimpleHtml("buz"))
      .prependChild(SimpleHtml("biz"))
      .toString shouldBe "<div><biz></biz>Hello World<buz></buz></div>"
  }
}
