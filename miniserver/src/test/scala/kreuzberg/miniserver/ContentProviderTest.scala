package kreuzberg.miniserver

import kreuzberg.i18n.ContentRenderer
import kreuzberg.testcore.TestBase

import java.util.concurrent.atomic.AtomicInteger

class ContentProviderTest extends TestBase {

  val config   = ContentConfig(Seq("test_content/about", "test_content/other"))
  val provider = ContentProvider.load(config)

  it should "fall back along the locale chain" in {
    provider.get("test_content/about", "de") shouldBe Some("<p>Deutsch</p>\n")
    provider.get("test_content/about", "de-AT") shouldBe Some("<p>Deutsch</p>\n")
    provider.get("test_content/about", "en") shouldBe Some("<p>English</p>\n")
    provider.get("test_content/about", "default") shouldBe Some("<p>English</p>\n")
    provider.get("test_content/other", "de") shouldBe Some("<p>Nur Englisch vorhanden</p>\n")
  }

  it should "only deliver configured documents" in {
    provider.get("test_content/unknown", "de") shouldBe None
    provider.get("test_content/about.de", "x") shouldBe None
  }

  it should "ignore invalid locales" in {
    provider.get("test_content/about", "../about.de") shouldBe Some("<p>English</p>\n")
  }

  it should "fail on missing default files" in {
    intercept[IllegalArgumentException] {
      ContentProvider.load(ContentConfig(Seq("test_content/missing")))
    }.getMessage shouldBe "Content resource not found: test_content/missing.default.html"
  }

  it should "render each file once" in {
    val count   = AtomicInteger()
    val counted = new ContentRenderer {
      override def extension: String              = "html"
      override def render(source: String): String = {
        count.incrementAndGet()
        source
      }
    }
    val p       = ContentProvider.load(config.copy(renderer = counted))
    count.get() shouldBe 2
    (0 until 3).foreach { _ =>
      p.get("test_content/about", "de")
      p.get("test_content/about", "de-CH")
      p.get("test_content/about", "en")
    }
    count.get() shouldBe 3
  }
}
