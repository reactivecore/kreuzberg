package kreuzberg.i18n

import kreuzberg.testcore.TestBase

import scala.scalajs.js

class TranslationsPlatformTest extends TestBase {

  it should "be empty without injected translations" in {
    TranslationsPlatform.load() shouldBe Translations.empty
  }

  it should "read injected translations" in {
    js.Dynamic.global.globalThis.kreuzbergTranslations = js.Dynamic.literal(
      locale = "de",
      available = js.Array("en", "de"),
      cookie = "lang",
      messages = js.Dictionary("hello" -> "Hallo %1")
    )
    try {
      val translations = TranslationsPlatform.load()
      translations.locale shouldBe "de"
      translations.available shouldBe Seq("en", "de")
      translations.format("hello", Seq("Welt")) shouldBe "Hallo Welt"
      translations.format("missing", Nil) shouldBe "missing"
    } finally {
      js.Dynamic.global.globalThis.kreuzbergTranslations = js.undefined
    }
  }
}
