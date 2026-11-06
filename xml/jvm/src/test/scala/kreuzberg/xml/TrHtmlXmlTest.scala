package kreuzberg.xml

import kreuzberg.*
import kreuzberg.i18n.{Translations, TranslationsPlatform, trHtml}
import kreuzberg.testcore.TestBase

class TrHtmlXmlTest extends TestBase {

  it should "take XML arguments and embed into XML" in {
    withMessages("index.source" -> "The source code is on %1.") {
      val html: Html = <div>{trHtml("index.source", <a href="http://x">GitHub</a>).wrap}</div>
      html.renderToString()
    } shouldBe """<div>The source code is on <a href="http://x">GitHub</a>.</div>"""
  }

  it should "embed components" in {
    withMessages("with.component" -> "Look: %1!") {
      Assembler.singleTree(() => Bar()).render()
    } shouldBe """<div data-id="1"><!-- Bar -->Look: <span data-id="2"><!-- Foo -->Boom!</span>!</div>"""
  }

  case class Bar() extends SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = <div>{trHtml("with.component", Foo()).wrap}</div>
  }

  case class Foo() extends SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = <span>Boom!</span>
  }

  private def withMessages[T](entries: (String, String)*)(f: => T): T = {
    val backend = new TranslationsPlatform.Backend {
      override def translations: Translations         = Translations.fromMap("de", Seq("de"), entries.toMap)
      override def switchLocale(locale: String): Unit = ()
    }
    TranslationsPlatform.withBackend(backend)(f)
  }
}
