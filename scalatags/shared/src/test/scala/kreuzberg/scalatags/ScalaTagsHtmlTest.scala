package kreuzberg.scalatags
import kreuzberg.{Assembler, ComponentId, Html}
import kreuzberg.imperative.{AssemblyContext, SimpleComponentBase, SimpleContext}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

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
      .withId(ComponentId(123))
      .renderToString() shouldBe """<div data-id="123">Hello World<span>How are you?</span></div>"""
  }

  it should "like comments" in {
    val c = simple.addComment("Boom").addComment("Bu--zz")
    c.renderToString() shouldBe """<div><!-- Buzz --><!-- Boom -->Hello World<span>How are you?</span></div>"""
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
      .html
      .renderToString() shouldBe """<div>Hello World<span data-id="1"><!-- Foo -->Boom!</span></div>"""
  }

  object Nested extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      val a = Foo.wrap
      val b = Foo.wrap
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

  it should "work nested" in {
    val rendered = Assembler
      .single(Nested)
      .html
      .renderToString()
    rendered shouldBe """<div><span data-id="1"><!-- Foo -->Boom!</span><p><div><span data-id="2"><!-- Foo -->Boom!</span></div></p></div>"""
  }

}
