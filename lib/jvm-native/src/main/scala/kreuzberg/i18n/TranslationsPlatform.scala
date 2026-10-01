package kreuzberg.i18n

/** Outside the browser there are no page translations; `tr` returns the key. */
private[i18n] object TranslationsPlatform {

  def load(): Translations = Translations.empty

  def switchLocale(locale: String): Unit = ()
}
