package kreuzberg.i18n

import kreuzberg.*
import kreuzberg.testcore.TestBase

class TrHtmlTest extends TestBase {

  it should "insert HTML arguments" in {
    withMessages("index.source" -> "The source code is on %1.") {
      trHtml("index.source", link("GitHub")).renderToString()
    } shouldBe """The source code is on <a href="x">GitHub</a>."""
  }

  it should "respect the order of placeholders" in {
    withMessages("order" -> "%2 und %1") {
      trHtml("order", bold("A"), "B").renderToString()
    } shouldBe "B und <b>A</b>"
  }

  it should "escape the catalog text and string arguments" in {
    withMessages("escaped" -> "<script>1 & 2</script> %1 %2 100%%") {
      trHtml("escaped", bold("x"), "<i>").renderToString()
    } shouldBe "&lt;script&gt;1 &amp; 2&lt;/script&gt; <b>x</b> &lt;i&gt; 100%"
  }

  it should "yield the key for missing translations" in {
    withMessages() {
      trHtml("missing.key", bold("x")).renderToString()
    } shouldBe "missing.key"
  }

  it should "leave missing arguments empty" in {
    withMessages("two" -> "%1 and %2") {
      trHtml("two", bold("x")).renderToString()
    } shouldBe "<b>x</b> and "
  }

  it should "insert each argument only once" in {
    withMessages("twice" -> "%1 or %1") {
      trHtml("twice", bold("x")).renderToString()
    } shouldBe "<b>x</b> or "
  }

  it should "embed components" in {
    val foo  = Foo()
    val html = withMessages("with.component" -> "Look: %1!")(trHtml("with.component", foo))
    html.embeddedComponents shouldBe Seq(foo)
    html.renderToString() shouldBe s"""Look: <component id="${foo.id}"/>!"""
  }

  it should "wrap into a span when used as an element" in {
    withMessages("plain" -> "Hello %1") {
      trHtml("plain", bold("x")).withId(Identifier(3)).renderToString()
    } shouldBe """<span data-id="3">Hello <b>x</b></span>"""
  }

  it should "not compile with a non literal key" in {
    assertDoesNotCompile("""val k = "x"; trHtml(k)""")
  }

  case class Foo() extends SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = SimpleHtml("span").addText("Boom!")
  }

  private def link(text: String): Html = SimpleHtml("a", attributes = Vector("href" -> Some("x"))).addText(text)

  private def bold(text: String): Html = SimpleHtml("b").addText(text)

  private def withMessages[T](entries: (String, String)*)(f: => T): T = {
    val backend = new TranslationsPlatform.Backend {
      override def translations: Translations         = Translations.fromMap("de", Seq("de"), entries.toMap)
      override def switchLocale(locale: String): Unit = ()
    }
    TranslationsPlatform.withBackend(backend)(f)
  }
}
