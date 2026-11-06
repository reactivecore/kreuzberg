package kreuzberg.markdown

import com.vladsch.flexmark.util.ast.Document
import kreuzberg.testcore.TestBase

class MarkdownRendererTest extends TestBase {

  val renderer = MarkdownRenderer()

  it should "render Markdown" in {
    renderer.render("Hello **World**") shouldBe "<p>Hello <strong>World</strong></p>\n"
  }

  it should "render tables" in {
    val html = renderer.render(
      """| A | B |
         ||---|---|
         || 1 | 2 |
         |""".stripMargin
    )
    html should include("<table>")
    html should include("<td>1</td>")
  }

  it should "render definition lists" in {
    val html = renderer.render(
      """Windows
        |: Run the installer.
        |""".stripMargin
    )
    html should include("<dl>")
    html should include("<dt>Windows</dt>")
    html should include("<dd>Run the installer.</dd>")
  }

  it should "render attributes" in {
    renderer.render("Run `sudo ./install.sh`{.cmd}.") should include("""<code class="cmd">sudo ./install.sh</code>""")
  }

  it should "render heading ids" in {
    renderer.render("# Get started") shouldBe """<h1 id="get-started">Get started</h1>""" + "\n"
    MarkdownRenderer(MarkdownOptions(headingIds = false)).render("# Get started") shouldBe "<h1>Get started</h1>\n"
  }

  it should "shift heading levels" in {
    val shifted = MarkdownRenderer(MarkdownOptions(headingOffset = 1))
    shifted.render("# A") should include("<h2")
    shifted.render("###### F") should include("<h6")
  }

  it should "list headings with the rendered ids" in {
    val source   =
      """# Usage
        |## Install
        |## Install
        |""".stripMargin
    val document = renderer.parse(source)
    val headings = renderer.headings(document)
    headings shouldBe Seq(
      MarkdownHeading(1, "Usage", "usage"),
      MarkdownHeading(2, "Install", "install"),
      MarkdownHeading(2, "Install", "install-1")
    )
    val html     = renderer.renderDocument(document)
    headings.foreach(h => html should include(s"""id="${h.id}""""))
  }

  it should "be customizable" in {
    val withToc = new MarkdownRenderer() {
      override def renderDocument(document: Document): String = {
        val toc = headings(document).map(h => s"""<a href="#${h.id}">${h.text}</a>""").mkString
        s"<nav>${toc}</nav>${super.renderDocument(document)}"
      }
    }
    withToc.render("# A") shouldBe """<nav><a href="#a">A</a></nav><h1 id="a">A</h1>""" + "\n"
  }
}
