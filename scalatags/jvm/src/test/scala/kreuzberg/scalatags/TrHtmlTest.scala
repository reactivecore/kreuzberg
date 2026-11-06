package kreuzberg.scalatags

import kreuzberg.*
import kreuzberg.i18n.{Translations, TranslationsPlatform, trHtml}
import kreuzberg.testcore.TestBase

class TrHtmlTest extends TestBase {

  it should "take ScalaTags arguments and embed into ScalaTags" in {
    withMessages("index.source" -> "The source code is on %1.") {
      div(trHtml("index.source", _root_.scalatags.Text.all.a(href := "http://x", "GitHub"))).render
    } shouldBe """<div>The source code is on <a href="http://x">GitHub</a>.</div>"""
  }

  it should "embed components" in {
    withMessages("with.component" -> "Look: %1!") {
      Assembler.singleTree(() => Bar()).render()
    } shouldBe """<div data-id="1"><!-- Bar -->Look: <span data-id="2"><!-- Foo -->Boom!</span>!</div>"""
  }

  case class Bar() extends SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = div(trHtml("with.component", Foo()))
  }

  case class Foo() extends SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = span("Boom!")
  }

  private def withMessages[T](entries: (String, String)*)(f: => T): T = {
    val backend = new TranslationsPlatform.Backend {
      override def translations: Translations         = Translations.fromMap("de", Seq("de"), entries.toMap)
      override def switchLocale(locale: String): Unit = ()
    }
    TranslationsPlatform.withBackend(backend)(f)
  }
}
