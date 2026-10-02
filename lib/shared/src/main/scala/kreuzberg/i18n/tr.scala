package kreuzberg.i18n

/**
 * Translates a key using the translations of the current page.
 *
 * The key must be a string literal, so that translations can be checked statically (see `TranslationCheck`). Further
 * arguments fill the placeholders `%1`..`%9`.
 *
 * Import it explicitly (`import kreuzberg.i18n.tr`), so that it takes precedence over the ScalaTags `tr` (table row)
 * tag from a wildcard import.
 */
inline def tr(inline key: String, args: Any*): String = {
  scala.compiletime.requireConst(key)
  Translations.current.format(key, args)
}
