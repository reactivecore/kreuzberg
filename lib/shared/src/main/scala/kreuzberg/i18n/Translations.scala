package kreuzberg.i18n

import kreuzberg.Logger

/** Messages of the active locale. */
trait Translations {

  /** Effective locale (e.g. `de`). */
  def locale: String

  /** Locales the server offers. */
  def available: Seq[String]

  /** Looks up the raw message. */
  def lookup(key: String): Option[String]

  /** Looks up and formats a message. A missing key yields the key itself. */
  final def format(key: String, args: Seq[Any]): String = {
    lookup(key) match {
      case Some(template) => MessageFormat.format(template, args)
      case None           =>
        Logger.debug(s"Missing translation for ${key} in locale ${locale}")
        key
    }
  }
}

object Translations {

  /**
   * Translations of the current page. In the browser, they are delivered by the MiniServer upon page load. On other
   * platforms this is empty, so `tr` returns the key.
   */
  lazy val current: Translations = TranslationsPlatform.load()

  /** Switches the locale (by Cookie) and reloads the page. No-op outside the browser. */
  def switchLocale(locale: String): Unit = TranslationsPlatform.switchLocale(locale)

  /** Translations backed by a Scala map. */
  def fromMap(locale: String, available: Seq[String], entries: Map[String, String]): Translations = {
    MapTranslations(locale, available, entries)
  }

  /** No translations, every key resolves to itself. */
  val empty: Translations = fromMap(Catalog.DefaultLocale, Nil, Map.empty)

  /** Name of the global variable the MiniServer injects. */
  val GlobalName = "kreuzbergTranslations"

  /** Default name of the cookie storing the selected locale. */
  val DefaultCookieName = "lang"

  private case class MapTranslations(locale: String, available: Seq[String], entries: Map[String, String])
      extends Translations {
    override def lookup(key: String): Option[String] = entries.get(key)
  }
}
