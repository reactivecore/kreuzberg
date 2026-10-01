package kreuzberg.i18n

import kreuzberg.testcore.TestBase

class CatalogSetTest extends TestBase {

  val default = Catalog("default", Map("a" -> "A", "b" -> "B", "c" -> "C"), "app.default.rctr")
  val de      = Catalog("de", Map("a" -> "A-de", "b" -> "B-de"), "app.de.rctr")
  val deAt    = Catalog("de-AT", Map("a" -> "A-at"), "app.de-at.rctr")
  val fr      = Catalog("fr", Map("a" -> "A-fr"), "app.fr.rctr")

  val set = CatalogSet(Seq(default, de, deAt, fr), "en").toOption.get

  it should "list available locales" in {
    set.available shouldBe Seq("en", "de", "de-at", "fr")
  }

  it should "resolve preferences" in {
    set.resolve(Seq("de-DE")) shouldBe "de"
    set.resolve(Seq("de-AT")) shouldBe "de-at"
    set.resolve(Seq("es", "fr-CA")) shouldBe "fr"
    set.resolve(Seq("en-US")) shouldBe "en"
    set.resolve(Seq("es")) shouldBe "en"
    set.resolve(Nil) shouldBe "en"
  }

  it should "merge key by key along the chain" in {
    set.merged("de-at") shouldBe Map("a" -> "A-at", "b" -> "B-de", "c" -> "C")
    set.merged("fr") shouldBe Map("a" -> "A-fr", "b" -> "B", "c" -> "C")
    set.merged("en") shouldBe default.entries
  }

  it should "validate" in {
    CatalogSet(Seq(de), "en") shouldBe Left("No catalog declares @locale \"default\"")
    CatalogSet(Seq(default, de, de.copy(source = "other.rctr")), "en") shouldBe Left(
      "Multiple catalogs for locale: de (app.de.rctr, other.rctr)"
    )
  }

  it should "parse sources" in {
    val result = CatalogSet.parse(Seq("a.rctr" -> "@locale \"default\"\nx: \"y\"", "b.rctr" -> "broken"), "en")
    result shouldBe Left("b.rctr:1: Expected '@', got 'b'")
  }
}
