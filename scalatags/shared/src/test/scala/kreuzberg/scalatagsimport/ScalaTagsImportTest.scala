// Outside of package kreuzberg.scalatags, so that only the imports below are in scope, like in user code.
package kreuzberg.scalatagsimport

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.testcore.TestBase

class ScalaTagsImportTest extends TestBase {
  it should "provide tags, attributes, styles and frag conversions" in {
    val html = div(
      cls      := "outer",
      data.foo := "bar",
      color    := "red",
      width    := 10.px,
      "text",
      3,
      input(tpe := "text", name := "x", disabled),
      raw("<b>raw</b>")
    )
    html.render shouldBe (
      """<div class="outer" data-foo="bar" style="color: red; width: 10px;">text3<input type="text" name="x" disabled="disabled" /><b>raw</b></div>"""
    )
  }

  it should "convert sequences, options and arrays of frags and modifiers" in {
    val html = div(
      Seq(span("a"), span("b")),
      Seq(cls      := "c", title := "t"),
      Option(span("o")),
      Option(style := "margin: 0"),
      Option.empty[Frag],
      Array(span("x")),
      Array(lang   := "en")
    )
    html.render shouldBe (
      """<div class="c" title="t" style="margin: 0" lang="en"><span>a</span><span>b</span><span>o</span><span>x</span></div>"""
    )
    frag(
      Seq(span("a")),
      Option(span("b")),
      Array(span("c"))
    ).render shouldBe "<span>a</span><span>b</span><span>c</span>"
  }

  it should "work inside components" in {
    Outer().assemble.html
      .renderToString() should fullyMatch regex """<div><span>Inner</span><component id="\d+"/></div>"""
  }
}

case class Inner() extends SimpleComponentBase {
  def assemble(using c: SimpleContext): Html = span("Inner")
}

case class Outer() extends SimpleComponentBase {
  def assemble(using c: SimpleContext): Html = div(span("Inner"), Inner().wrap)
}
