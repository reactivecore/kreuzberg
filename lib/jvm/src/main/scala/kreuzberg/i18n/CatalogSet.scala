package kreuzberg.i18n

/**
 * A validated set of catalogs: exactly one `default` catalog and at most one catalog per locale.
 *
 * @param defaultLocale
 *   the language the default catalog is written in (e.g. `en`)
 */
case class CatalogSet private (catalogs: Seq[Catalog], defaultLocale: String) {

  /** The schema catalog. */
  def default: Catalog = catalogs.find(_.isDefault).get // guaranteed by construction

  /** Available (normalized) locales, starting with the default locale. */
  val available: Seq[String] = {
    val translated = catalogs.filterNot(_.isDefault).map(c => LocaleChain.normalize(c.locale))
    (LocaleChain.normalize(defaultLocale) +: translated).distinct
  }

  private val byLocale: Map[String, Catalog] = catalogs.map(c => LocaleChain.normalize(c.locale) -> c).toMap

  /**
   * Resolves locale preferences (most preferred first) to the effective locale: the first chain entry an available
   * locale exists for, otherwise the default locale.
   */
  def resolve(preferences: Seq[String]): String = {
    LocaleChain(preferences).find(available.contains).getOrElse(available.head)
  }

  /** Returns all messages for an effective locale, falling back key by key along its chain. */
  def merged(locale: String): Map[String, String] = {
    val chain = LocaleChain(Seq(locale))
    chain.reverse.flatMap(byLocale.get).foldLeft(Map.empty[String, String]) { (acc, catalog) =>
      acc ++ catalog.entries
    }
  }
}

object CatalogSet {

  def apply(catalogs: Seq[Catalog], defaultLocale: String): Either[String, CatalogSet] = {
    val defaults   = catalogs.filter(_.isDefault)
    val duplicates = catalogs.groupBy(c => LocaleChain.normalize(c.locale)).collect {
      case (locale, cs) if cs.size > 1 => s"${locale} (${cs.map(_.source).mkString(", ")})"
    }
    if (defaults.isEmpty) {
      Left(s"""No catalog declares @locale "${Catalog.DefaultLocale}"""")
    } else if (duplicates.nonEmpty) {
      Left(s"Multiple catalogs for locale: ${duplicates.mkString(", ")}")
    } else {
      Right(new CatalogSet(catalogs, defaultLocale))
    }
  }

  /** Parses and validates catalogs given as (source name, content). */
  def parse(sources: Seq[(String, String)], defaultLocale: String): Either[String, CatalogSet] = {
    val parsed = sources.map { case (name, content) => CatalogParser.parse(content, name) }
    val errors = parsed.collect { case Left(e) => e.toString }
    if (errors.nonEmpty) {
      Left(errors.mkString("\n"))
    } else {
      apply(parsed.collect { case Right(c) => c }, defaultLocale)
    }
  }
}
