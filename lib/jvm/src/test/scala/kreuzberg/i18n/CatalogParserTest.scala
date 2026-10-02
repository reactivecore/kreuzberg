package kreuzberg.i18n

import kreuzberg.testcore.TestBase

class CatalogParserTest extends TestBase {

  it should "parse a simple catalog" in {
    val content =
      """// German translations
        |@locale "de"
        |
        |login.title:   "Anmelden"
        |login.submit:  "Absenden";
        |/* block
        |   comment */
        |greeting: "Hallo %1, du hast %2 Nachrichten"
        |""".stripMargin
    CatalogParser.parse(content, "app.de.rctr") shouldBe Right(
      Catalog(
        "de",
        Map(
          "login.title"  -> "Anmelden",
          "login.submit" -> "Absenden",
          "greeting"     -> "Hallo %1, du hast %2 Nachrichten"
        ),
        "app.de.rctr"
      )
    )
  }

  it should "parse an empty catalog" in {
    CatalogParser.parse("@locale \"default\"") shouldBe Right(Catalog("default", Map.empty))
  }

  it should "handle escapes" in {
    val content = """@locale "fr"
                    |a: "Quote \" Backslash \\ Tab\tNL\nCR\r"
                    |b: "\u{e9}t\u{E9} \u{1F600}"
                    |""".stripMargin
    val catalog = CatalogParser.parse(content).toOption.get
    catalog.entries("a") shouldBe "Quote \" Backslash \\ Tab\tNL\nCR\r"
    catalog.entries("b") shouldBe "été \uD83D\uDE00"
  }

  it should "report errors with line numbers" in {
    def error(content: String): CatalogError = CatalogParser.parse(content, "x.rctr").swap.toOption.get

    error("a: \"b\"") shouldBe CatalogError("x.rctr", 1, "Expected '@', got 'a'")
    error("@lang \"de\"").message shouldBe "Expected @locale, got @lang"
    error("@locale \"de\"\n\nfoo \"bar\"") shouldBe CatalogError("x.rctr", 3, "Expected ':', got '\"'")
    error("@locale \"de\"\nfoo: \"bar") shouldBe CatalogError("x.rctr", 2, "Unterminated string")
    error("@locale \"de\"\nfoo: \"a\\q\"").message shouldBe "Invalid escape \\q"
    error("@locale \"de\"\nfoo: \"\\u{d800}\"").message shouldBe "Invalid unicode scalar value \\u{d800}"
    error("@locale \"de\"\nfoo.: \"x\"").message shouldBe "Expected identifier, got ':'"
    error("@locale \"de\"\na: \"1\"\na: \"2\"") shouldBe CatalogError("x.rctr", 3, "Duplicate key a")
    error("@locale \"de\"\n/* open") shouldBe CatalogError("x.rctr", 2, "Unterminated comment")
  }
}
