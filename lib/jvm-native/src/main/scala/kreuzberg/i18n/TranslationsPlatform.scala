package kreuzberg.i18n

import kreuzberg.SimpleThreadLocal

/**
 * Outside the browser, translations are provided by a [[TranslationsPlatform.Backend]] per thread, e.g. by a
 * server-side engine for the current session. Without one, there are no translations and `tr` returns the key.
 */
private[kreuzberg] object TranslationsPlatform {

  /** Provides translations, e.g. of a session. */
  trait Backend {
    def translations: Translations

    /** Called by [[Translations.switchLocale]]. */
    def switchLocale(locale: String): Unit
  }

  private object NoBackend extends Backend {
    override def translations: Translations         = Translations.empty
    override def switchLocale(locale: String): Unit = ()
  }

  private val backend = new SimpleThreadLocal[Backend](NoBackend)

  /** Run `f` with a backend for the current thread. */
  def withBackend[T](b: Backend)(f: => T): T = backend.withInstance(b)(f)

  def current: Translations = backend.get().translations

  def switchLocale(locale: String): Unit = backend.get().switchLocale(locale)
}
