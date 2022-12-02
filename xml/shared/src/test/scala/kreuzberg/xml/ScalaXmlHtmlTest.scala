package kreuzberg.xml

import kreuzberg.imperative.{SimpleComponentBase, SimpleContext}
import kreuzberg._

class ScalaXmlHtmlTest extends TestBase {

  val sample = <hello>World</hello>

  it should "work in simple case" in {
    val packed = ScalaXmlHtml(sample)
    packed.renderToString() shouldBe "<hello>World</hello>"
    packed.withId(ComponentId(123)).renderToString() shouldBe """<hello data-id="123">World</hello>"""
    packed.placeholders shouldBe empty
    packed
      .addInner(
        Seq(
          ScalaXmlHtml(<span>!</span>),
          ScalaXmlHtml(<span>!!</span>)
        )
      )
      .renderToString() shouldBe "<hello>World<span>!</span><span>!!</span></hello>"
  }

  import kreuzberg.xml._
  case class Child(name: String) extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      <div>This is a child of {name}</div>
    }
  }

  object Text extends SimpleComponentBase {
    override def assemble(implicit c: SimpleContext): Html = {
      // Note: you have to use wrap
      val c1 = anonymousChild(Child("Hello"))
      val c2 = anonymousChild(Child("World"))
      <div>This is data {c1.wrap},{c2.wrap}</div>
    }
  }

  it should "work in the packed case" in {
    val node = Assembler.single(Text)
    val html = node
      .renderWithId(ComponentId(0))

    html.placeholders.map(_.id.id) shouldBe Seq(1, 2)
    html.renderToString() shouldBe """<div data-id="0">This is data <div data-id="1">This is a child of Hello</div>,<div data-id="2">This is a child of World</div></div>"""
  }
}
