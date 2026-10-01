package kreuzberg.i18n

/**
 * A translation catalog for one locale (parsed from a `.rctr` file).
 *
 * @param locale
 *   declared locale, `default` for the schema catalog
 * @param entries
 *   key to message
 * @param source
 *   name of the source (e.g. file name), for diagnostics
 */
case class Catalog(locale: String, entries: Map[String, String], source: String = "") {
  def isDefault: Boolean = locale == Catalog.DefaultLocale
}

object Catalog {

  /** Locale name of the schema catalog. */
  val DefaultLocale = "default"
}

/** Error during parsing a catalog. */
case class CatalogError(source: String, line: Int, message: String) {
  override def toString: String = s"${source}:${line}: ${message}"
}
