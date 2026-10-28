package kreuzberg.i18n

/**
 * Translates a key using the translations of the current page.
 *
 * The key must be a string literal, so that translations can be checked statically (see `TranslationCheck`). Further
 * arguments fill the placeholders `%1`..`%9`.
 *
 * Call it while rendering (e.g. in `assemble` or a `def`), not once in an `object` initializer: with server-side
 * rendering, sessions have different locales, and the first one would be frozen for all.
 *
 * Import it explicitly (`import kreuzberg.i18n.tr`), so that it takes precedence over the ScalaTags `tr` (table row)
 * tag from a wildcard import.
 */
inline def tr(inline key: String, args: Any*): String = {
  scala.compiletime.requireConst(key)
  Translations.current.format(key, args)
}
