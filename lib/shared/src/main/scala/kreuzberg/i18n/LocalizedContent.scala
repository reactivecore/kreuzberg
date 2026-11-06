package kreuzberg.i18n

import kreuzberg.*

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Embeds a content file (e.g. Markdown) in the locale of the current page, rendered on the server.
 *
 * For the name `content/about` and locale `de-at`, the server takes the first existing file of
 * `content/about.de-at.md`, `content/about.de.md` and `content/about.default.md` (the extension depends on the
 * renderer). Documents must be configured on the server (`ContentConfig` in `kreuzberg-miniserver`).
 *
 * The content comes from the application's resources; it is trusted and embedded as raw HTML.
 *
 * With a server-side engine, the content is rendered directly. In the browser, it is fetched from the MiniServer, and
 * the component stays empty until it arrives.
 */
case class LocalizedContent(name: String) extends SimpleComponentBase {
  private val loaded = Model.create[Option[Try[String]]](None)

  override def assemble(using sc: SimpleContext): Html = {
    val state = subscribe(loaded).orElse {
      val future = LocalizedContent.load(name)
      if (future.value.isEmpty) {
        future.onComplete(result => loaded.set(Some(result)))
      }
      future.value
    }
    state match {
      case None                => SimpleHtml("div")
      case Some(Success(html)) => SimpleHtml("div").addChild(SimpleHtmlNode.Raw(html))
      case Some(Failure(e))    =>
        // Debug only: on the server this runs on every render
        Logger.debug(s"Could not load content ${name}: ${e.getMessage}")
        SimpleHtml("div").addText(s"Could not load content ${name}")
    }
  }
}

object LocalizedContent {

  /** Loads the rendered content of a document in the locale of the current page. */
  def load(name: String): Future[String] = ContentPlatform.load(name)

  /** Path of the MiniServer endpoint delivering content, with query parameters `name` and `locale`. */
  val EndpointPath: Seq[String] = Seq("krz", "content")
}
