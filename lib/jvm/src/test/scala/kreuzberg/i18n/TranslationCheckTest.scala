package kreuzberg.i18n

import kreuzberg.testcore.TestBase

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class TranslationCheckTest extends TestBase {

  val default = Catalog("default", Map("a" -> "A", "b" -> "B %1", "c" -> "C"), "app.default.rctr")
  val de      = Catalog("de", Map("a" -> "A-de", "b" -> "B-de", "x" -> "X"), "app.de.rctr")
  val fr      = Catalog("fr", Map("a" -> "A-fr", "b" -> "B-fr %1", "c" -> "C-fr"), "app.fr.rctr")

  def call(key: String, args: Int = 0): TrCall = TrCall(key, args, "X.scala", 1)

  it should "pass for consistent usage" in {
    TranslationCheck.check(Seq(call("a"), call("b", 1), call("c")), Seq(default, fr)) shouldBe TranslationCheck
      .Report(Nil, Nil, Nil)
  }

  it should "report all problems" in {
    val report = TranslationCheck.check(Seq(call("a"), call("b"), call("unknown")), Seq(default, de, fr))
    report.errors shouldBe Seq(
      "X.scala:1: b expects 1 arguments, got 0",
      "X.scala:1: Unknown key unknown"
    )
    report.warnings shouldBe Seq(
      "de: 1 of 3 keys untranslated (c)",
      "de: 1 keys not in default catalog (x)",
      "app.de.rctr: Placeholders of b differ from default catalog"
    )
    report.infos shouldBe Seq("1 keys not referenced by tr(...): c")
    report.failed(strict = false) shouldBe true
  }

  it should "consider the fallback chain for untranslated keys" in {
    val deFull = Catalog("de", Map("a" -> "A-de", "b" -> "B-de %1", "c" -> "C-de"), "app.de.rctr")
    val deCh   = Catalog("de-CH", Map("a" -> "A-ch"), "app.de-ch.rctr")
    val itCh   = Catalog("it-CH", Map("a" -> "A-it"), "app.it-ch.rctr")
    val report = TranslationCheck.check(Seq(call("a"), call("b", 1), call("c")), Seq(default, deFull, deCh, itCh))
    report.warnings shouldBe Seq("it-CH: 2 of 3 keys untranslated (b, c)")
  }

  it should "fail only in strict mode on warnings" in {
    val report =
      TranslationCheck.check(Seq(call("a"), call("b", 1), call("c")), Seq(default, de.copy(entries = Map("a" -> "x"))))
    report.errors shouldBe empty
    report.failed(strict = false) shouldBe false
    report.failed(strict = true) shouldBe true
  }

  it should "report catalog set errors" in {
    TranslationCheck.check(Nil, Seq(de)).errors shouldBe Seq("No catalog declares @locale \"default\"")
  }

  it should "work on files" in {
    val dir                                        = Files.createTempDirectory("translation-check")
    def write(name: String, content: String): Path = {
      val path = dir.resolve(name)
      Files.createDirectories(path.getParent)
      Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    }
    write("src/Page.scala", """div(tr("hello", name))""")
    write("src/target/Generated.scala", """tr("ignored")""")
    write("i18n/app.default.rctr", "@locale \"default\"\nhello: \"Hello %1\"")
    write("i18n/app.de.rctr", "@locale \"de\"\nhello: \"Hallo %1\"")
    TranslationCheck.run(Seq(dir.resolve("src")), Seq(dir.resolve("i18n"))) shouldBe TranslationCheck.Report(
      Nil,
      Nil,
      Nil
    )

    write("i18n/broken.rctr", "nope")
    TranslationCheck
      .run(Seq(dir.resolve("src")), Seq(dir.resolve("i18n")))
      .errors
      .map(
        _.stripPrefix(dir.toString)
      ) shouldBe Seq("/i18n/broken.rctr:1: Expected '@', got 'n'")
  }
}
