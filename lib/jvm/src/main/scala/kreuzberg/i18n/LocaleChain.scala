package kreuzberg.i18n

/**
 * Expands locale preferences into a lookup order.
 *
 * Each tag is normalized (lowercased, `_` to `-`, encoding suffixes dropped) and expanded by dropping subtags. The
 * results are concatenated, deduplicated and `default` is appended last.
 *
 * {{{
 * Seq("de-DE")          -> Seq("de-de", "de", "default")
 * Seq("fr-CA", "en-GB") -> Seq("fr-ca", "fr", "en-gb", "en", "default")
 * }}}
 */
object LocaleChain {

  def apply(preferences: Seq[String]): Seq[String] = {
    (preferences.flatMap(expand) :+ Catalog.DefaultLocale).distinct
  }

  /** Normalizes a single locale tag. Returns an empty string for `C`, `POSIX` and empty tags. */
  def normalize(tag: String): String = {
    val withoutSuffix = tag.trim.takeWhile(c => c != '.' && c != '@')
    val normalized    = withoutSuffix.toLowerCase.replace('_', '-')
    if (normalized == "c" || normalized == "posix") "" else normalized
  }

  /** Expands a single tag by dropping subtags, e.g. `zh-Hant-TW` -> `zh-hant-tw`, `zh-hant`, `zh`. */
  def expand(tag: String): Seq[String] = {
    val normalized = normalize(tag)
    if (normalized.isEmpty) {
      Nil
    } else {
      val parts = normalized.split('-').toSeq.filter(_.nonEmpty)
      parts.indices.reverse.map(i => parts.take(i + 1).mkString("-"))
    }
  }
}
