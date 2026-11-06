package kreuzberg.engine.server

import kreuzberg.*
import kreuzberg.extras.UrlResource
import kreuzberg.i18n.{ContentPlatform, LocalizedContent, Translations}
import kreuzberg.scalatags.*
import kreuzberg.testcore.TestBase

import scala.concurrent.duration.*

class LocalizedContentTest extends TestBase {

  it should "render content in the session's locale" in withManager { manager =>
    val (english, sessionId) = open(manager, None, "en")
    english should include("<p>English</p>")
    val (german, _)          = open(manager, Some(sessionId), "de")
    german should include("<p>Deutsch</p>")
  }

  it should "render an error for unknown content" in {
    val manager = SessionManager(Unknown, () => ServiceRepository.empty, 1.hour, backend)
    try {
      manager.openPage(None, UrlResource("/"))(_.tree.render())._1 should include("Could not load content unknown")
    } finally manager.close()
  }

  private object Root extends SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = div(LocalizedContent("about").wrap)
  }

  it should "render an error if the backend fails" in {
    val failing = new ContentPlatform.Backend {
      override def load(name: String): Option[String] = throw new IllegalStateException("broken")
    }
    val manager = SessionManager(Root, () => ServiceRepository.empty, 1.hour, failing)
    try {
      manager.openPage(None, UrlResource("/"))(_.tree.render())._1 should include("Could not load content about")
    } finally manager.close()
  }

  private object Unknown extends SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = div(LocalizedContent("unknown").wrap)
  }

  private val backend = new ContentPlatform.Backend {
    override def load(name: String): Option[String] = Option.when(name == "about") {
      if (Translations.current.locale == "de") "<p>Deutsch</p>" else "<p>English</p>"
    }
  }

  private def withManager[T](f: SessionManager => T): T = {
    val manager = SessionManager(Root, () => ServiceRepository.empty, 1.hour, backend)
    try f(manager)
    finally manager.close()
  }

  private def open(manager: SessionManager, id: Option[String], locale: String): (String, String) = {
    val translations = Translations.fromMap(locale, Seq("en", "de"), Map.empty)
    manager.openPage(id, UrlResource("/"), translations)(s => (s.tree.render(), s.id))._1
  }
}
