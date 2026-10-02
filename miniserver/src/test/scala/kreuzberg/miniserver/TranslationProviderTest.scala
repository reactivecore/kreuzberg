package kreuzberg.miniserver

import kreuzberg.testcore.TestBase

class TranslationProviderTest extends TestBase {

  val provider = TranslationProvider.load(
    TranslationConfig(Seq("test_i18n/test.default.rctr", "test_i18n/test.de.rctr"))
  )

  it should "parse Accept-Language headers" in {
    TranslationProvider.parseAcceptLanguage("de-DE,de;q=0.9,en;q=0.8,*;q=0.5") shouldBe Seq("de-DE", "de", "en")
    TranslationProvider.parseAcceptLanguage("en;q=0.2, fr, de;q=0") shouldBe Seq("fr", "en")
    TranslationProvider.parseAcceptLanguage("") shouldBe Nil
  }

  it should "resolve the locale by cookie, then header, then default" in {
    def locale(headers: Seq[(String, String)] = Nil, cookies: Seq[(String, String)] = Nil): String =
      provider.forRequest(InitRequest(headers, cookies)).locale

    locale() shouldBe "en"
    locale(headers = Seq("accept-language" -> "de-AT")) shouldBe "de"
    locale(headers = Seq("Accept-Language" -> "fr")) shouldBe "en"
    locale(headers = Seq("Accept-Language" -> "de"), cookies = Seq("lang" -> "en")) shouldBe "en"
    locale(cookies = Seq("lang" -> "unknown")) shouldBe "en"
  }

  it should "merge messages with the default catalog" in {
    val de = provider.forRequest(InitRequest(Nil, Seq("lang" -> "de")))
    de.available shouldBe Seq("en", "de")
    de.messages shouldBe Map("greeting" -> "Hallo %1", "script.close" -> "</script>")
  }

  it should "reuse the rendered script per locale" in {
    val first  = provider.forRequest(InitRequest(Nil, Seq("lang" -> "de")))
    val second = provider.forRequest(InitRequest(Seq("Accept-Language" -> "de-DE"), Nil))
    first should be theSameInstanceAs second
    first.scriptCode should startWith("""window.kreuzbergTranslations = {"locale":"de",""")
    first.scriptCode should include(""""script.close":"<\/script>"""")
  }

  it should "fail on missing or invalid resources" in {
    an[IllegalArgumentException] shouldBe thrownBy {
      TranslationProvider.load(TranslationConfig(Seq("test_i18n/missing.rctr")))
    }
    an[IllegalArgumentException] shouldBe thrownBy {
      TranslationProvider.load(TranslationConfig(Seq("test_i18n/test.de.rctr")))
    }
  }
}
