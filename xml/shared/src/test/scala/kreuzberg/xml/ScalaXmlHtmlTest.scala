package kreuzberg.xml

import kreuzberg.*
import kreuzberg.testcore.TestBase

class ScalaXmlHtmlTest extends TestBase {

  val sample = <hello>World</hello>

  it should "work in simple case" in {
    val packed = ScalaXmlHtml(sample)
    packed.renderToString() shouldBe "<hello>World</hello>"
    packed.withId(Identifier(123)).renderToString() shouldBe """<hello data-id="123">World</hello>"""
    packed.embeddedComponents shouldBe empty
  }

  it should "like comments" in {
    ScalaXmlHtml(sample)
      .addComment("Boom")
      .addComment("Bu--zz")
      .renderToString() shouldBe "<hello><!-- Buzz --><!-- Boom -->World</hello>"
  }

  import kreuzberg.xml._
  case class Child(name: String) extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      <div>This is a child of {name}</div>
    }
  }

  case class Text() extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      // Note: you have to use wrap
      val c1 = Child("Hello")
      val c2 = Child("World")
      <div>This is data {c1.wrap},{c2.wrap}</div>
    }
  }

  it should "work in the packed case" in {
    val node = Assembler.single(() => Text())
    val html = node.html

    html.embeddedComponents.map(_.id.value) shouldBe Seq(2, 3)
    html.renderToString() shouldBe """<div>This is data <component id="2"/>,<component id="3"/></div>"""

    val fullHtml = Assembler.singleTree(() => Text()).render()
    fullHtml shouldBe """<div data-id="1"><!-- Text -->This is data <div data-id="2"><!-- Child -->This is a child of Hello</div>,<div data-id="3"><!-- Child -->This is a child of World</div></div>"""
  }

  it should "append and prepend" in {
    val html = ScalaXmlHtml(<div>Hello World</div>)
    html
      .appendChild(
        ScalaXmlHtml(<h1>Boom</h1>)
      )
      .prependChild(
        ScalaXmlHtml(<h2>Bar</h2>)
      )
      .toString shouldBe "<div><h2>Bar</h2>Hello World<h1>Boom</h1></div>"
    html
      .appendChild(SimpleHtml("buz"))
      .prependChild(SimpleHtml("biz"))
      .toString shouldBe "<div><biz></biz>Hello World<buz></buz></div>"
  }
}
