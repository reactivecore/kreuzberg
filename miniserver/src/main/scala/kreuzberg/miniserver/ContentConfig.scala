package kreuzberg.miniserver

import kreuzberg.i18n.{ContentRenderer, LocaleChain, LocalizedContent, Translations}
import kreuzberg.miniserver.MiniServer.MiniServerEndpoint
import sttp.model.StatusCode
import sttp.tapir.*

import java.util.concurrent.ConcurrentHashMap

/**
 * Configures per-locale content files, as embedded by [[kreuzberg.i18n.LocalizedContent]].
 *
 * A document `content/about` consists of class path resources `content/about.default.md` (required) and optionally
 * `content/about.<locale>.md`, e.g. `content/about.de.md`.
 *
 * @param documents
 *   document names (resource paths without locale and extension)
 * @param renderer
 *   renders the files, e.g. `MarkdownRenderer` from `kreuzberg-markdown`; it also decides on the file extension
 */
case class ContentConfig(
    documents: Seq[String],
    renderer: ContentRenderer = ContentRenderer.Html
) {

  /** The provider of this configuration, loaded on first use and shared by all users of the configuration. */
  lazy val provider: ContentProvider = ContentProvider.load(this)
}

/**
 * Loads and renders content files. Default files are rendered at construction, so missing ones fail at startup; files
 * of other locales are rendered on first use. Each file is rendered once.
 */
class ContentProvider private (config: ContentConfig, classLoader: ClassLoader, defaults: Map[String, String]) {

  /** Rendered files by resource path. */
  private val rendered = ConcurrentHashMap[String, String]()

  /**
   * Returns the rendered document for a locale, falling back along the [[LocaleChain]] to the default file. Returns
   * `None` for unknown documents.
   */
  def get(name: String, locale: String): Option[String] = {
    defaults.get(name).map { default =>
      LocaleChain(Seq(locale)).iterator
        .filter(l => l != Translations.DefaultLocale && ContentProvider.ValidLocale.matches(l))
        .flatMap(l => renderResource(s"${name}.${l}.${config.renderer.extension}"))
        .nextOption()
        .getOrElse(default)
    }
  }

  /** Atomic per path; missing files are not cached (null), as locales come from requests. */
  private def renderResource(path: String): Option[String] = {
    Option(rendered.computeIfAbsent(path, p => Resources.readUtf8(classLoader, p).map(config.renderer.render).orNull))
  }
}

object ContentProvider {

  /** Loads and renders the default files; throws if one is missing. */
  def load(config: ContentConfig, classLoader: ClassLoader = getClass.getClassLoader): ContentProvider = {
    val defaults = config.documents.map { name =>
      val path   = s"${name}.${Translations.DefaultLocale}.${config.renderer.extension}"
      val source = Resources.readUtf8(classLoader, path).getOrElse {
        throw new IllegalArgumentException(s"Content resource not found: ${path}")
      }
      name -> config.renderer.render(source)
    }.toMap
    new ContentProvider(config, classLoader, defaults)
  }

  /** Endpoint delivering rendered content to the browser, see [[LocalizedContent.EndpointPath]]. */
  def contentEndpoint(provider: ContentProvider): MiniServerEndpoint = {
    val path = LocalizedContent.EndpointPath.map(stringToPath).foldLeft(emptyInput)(_ / _)
    endpoint.get
      .in(path)
      .in(query[String]("name"))
      .in(query[Option[String]]("locale"))
      .errorOut(statusCode)
      .out(htmlBodyUtf8)
      .handle { case (name, locale) =>
        provider.get(name, locale.getOrElse(Translations.DefaultLocale)).toRight(StatusCode.NotFound)
      }
  }

  /** Locales as part of resource paths; others (e.g. with `/`) are ignored. */
  private val ValidLocale = "[a-z0-9-]+".r
}
