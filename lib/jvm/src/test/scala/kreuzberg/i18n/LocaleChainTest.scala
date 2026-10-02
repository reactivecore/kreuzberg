package kreuzberg.i18n

import kreuzberg.testcore.TestBase

class LocaleChainTest extends TestBase {

  it should "expand preference lists" in {
    LocaleChain(Seq("de-DE")) shouldBe Seq("de-de", "de", "default")
    LocaleChain(Seq("zh-Hant-TW")) shouldBe Seq("zh-hant-tw", "zh-hant", "zh", "default")
    LocaleChain(Seq("fr-CA", "en-GB")) shouldBe Seq("fr-ca", "fr", "en-gb", "en", "default")
    LocaleChain(Seq("de", "de-AT")) shouldBe Seq("de", "de-at", "default")
    LocaleChain(Seq("C")) shouldBe Seq("default")
    LocaleChain(Seq("POSIX")) shouldBe Seq("default")
    LocaleChain(Nil) shouldBe Seq("default")
  }

  it should "normalize tags" in {
    LocaleChain.normalize("de_DE.UTF-8") shouldBe "de-de"
    LocaleChain.normalize("sr_RS@latin") shouldBe "sr-rs"
    LocaleChain.normalize(" EN ") shouldBe "en"
  }
}
