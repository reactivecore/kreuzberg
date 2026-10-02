package kreuzberg.miniserver

import io.circe.Json
import kreuzberg.i18n.{CatalogSet, LocaleChain, Translations}

import java.nio.charset.StandardCharsets
import scala.util.Using

/**
 * Configures translations, which are delivered to the client on page load.
 *
 * @param resources
 *   class path resources of `.rctr` catalogs, exactly one must declare `@locale "default"`
 * @param defaultLocale
 *   the language the default catalog is written in
 * @param cookieName
 *   cookie which holds the selected locale
 */
case class TranslationConfig(
    resources: Seq[String],
    defaultLocale: String = "en",
    cookieName: String = Translations.DefaultCookieName
)

/** Translations of one locale, as delivered with the index page. Created once per locale. */
case class PageTranslations(locale: String, available: Seq[String], cookieName: String, messages: Map[String, String]) {

  /** JSON payload, as read by the client. */
  def toJson: Json = Json.obj(
    "locale"    -> Json.fromString(locale),
    "available" -> Json.fromValues(available.map(Json.fromString)),
    "cookie"    -> Json.fromString(cookieName),
    "messages"  -> Json.fromFields(messages.toSeq.sortBy(_._1).map { case (k, v) => k -> Json.fromString(v) })
  )

  /** JavaScript for the index page, rendered once. */
  val scriptCode: String = {
    // JSON is valid JavaScript; escaping "</" prevents closing the script tag early
    val json = toJson.noSpaces.replace("</", "<\\/")
    s"window.${Translations.GlobalName} = ${json};"
  }
}

/** Loads catalogs once and resolves the translations for each request. */
class TranslationProvider(config: TranslationConfig, catalogSet: CatalogSet) {
  private val byLocale: Map[String, PageTranslations] = catalogSet.available.map { locale =>
    locale -> PageTranslations(locale, catalogSet.available, config.cookieName, catalogSet.merged(locale))
  }.toMap

  /** Resolves translations: cookie first, then Accept-Language, then the default locale. */
  def forRequest(request: InitRequest): PageTranslations = {
    val fromCookie         = request.cookies.collect { case (name, value) if name == config.cookieName => value }
    val fromAcceptLanguage = request.headers.collect {
      case (name, value) if name.equalsIgnoreCase("Accept-Language") => TranslationProvider.parseAcceptLanguage(value)
    }.flatten
    byLocale(catalogSet.resolve(fromCookie ++ fromAcceptLanguage))
  }
}

object TranslationProvider {

  /** Loads catalogs from class path; throws on missing or invalid catalogs. */
  def load(config: TranslationConfig, classLoader: ClassLoader = getClass.getClassLoader): TranslationProvider = {
    val sources = config.resources.map { name =>
      val stream = Option(classLoader.getResourceAsStream(name)).getOrElse {
        throw new IllegalArgumentException(s"Translation resource not found: ${name}")
      }
      name -> Using.resource(stream)(s => new String(s.readAllBytes(), StandardCharsets.UTF_8))
    }
    CatalogSet.parse(sources, config.defaultLocale) match {
      case Left(error) => throw new IllegalArgumentException(s"Invalid translations: ${error}")
      case Right(set)  => new TranslationProvider(config, set)
    }
  }

  /** Parses an Accept-Language header into tags, most preferred first. */
  def parseAcceptLanguage(value: String): Seq[String] = {
    value
      .split(',')
      .toSeq
      .flatMap { part =>
        part.split(';').toList.map(_.trim) match {
          case tag :: params if tag.nonEmpty && tag != "*" =>
            val quality = params.collectFirst {
              case p if p.startsWith("q=") => p.stripPrefix("q=").toDoubleOption.getOrElse(0.0)
            }
            Some(tag -> quality.getOrElse(1.0))
          case _                                           => None
        }
      }
      .filter(_._2 > 0)
      .sortBy(-_._2)
      .map(_._1)
      .filter(t => LocaleChain.normalize(t).nonEmpty)
  }
}
