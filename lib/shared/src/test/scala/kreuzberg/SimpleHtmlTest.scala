package kreuzberg

class SimpleHtmlTest extends TestBase {

  it should "work for a trivial case" in {
    val node = SimpleHtml("div")
    node.toString shouldBe "<div></div>"

    node.withId(Identifier(3)).toString shouldBe "<div data-id=\"3\"></div>"
    node.withId(Identifier(4)).toString shouldBe "<div data-id=\"4\"></div>"
  }

  it should "work for a complex case" in {
    val node = SimpleHtml(
      "div",
      attributes = Vector(
        "a" -> None,
        "b" -> Some("1234")
      ),
      children = Vector(
        SimpleHtmlNode.Text("Lierumlarumlöffelstiehl"),
        SimpleHtml(
          "b",
          children = Vector(
            SimpleHtmlNode.Text("Hello")
          )
        )
      )
    )
    node.toString shouldBe ("<div a b=\"1234\">Lierumlarumlöffelstiehl<b>Hello</b></div>")
  }

  it should "escape" in {
    val bad        = "bad<>\"'&"
    val badEscaped = "bad&lt;&gt;&quot;&#039;&amp;"
    val node       = SimpleHtml(
      bad,
      attributes = Vector(
        bad -> None,
        bad -> Some(bad)
      ),
      children = Vector(
        SimpleHtmlNode.Text(bad)
      )
    )
    node.toString shouldBe s"<$badEscaped $badEscaped $badEscaped=\"$badEscaped\">$badEscaped</$badEscaped>"
  }
}
