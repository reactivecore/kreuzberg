package kreuzberg.i18n

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.URIUtils

/** Browser side: reads the translations injected by the MiniServer. */
private[i18n] object TranslationsPlatform {

  def load(): Translations = {
    // typeof on a global is safe, even if it (or window) is not defined, e.g. in Node.js
    if (js.typeOf(js.Dynamic.global.kreuzbergTranslations) != "object") {
      Translations.empty
    } else {
      js.Dynamic.global.kreuzbergTranslations.asInstanceOf[Payload] match {
        case null    => Translations.empty
        case payload => JsTranslations(payload)
      }
    }
  }

  def switchLocale(locale: String): Unit = {
    val cookieName = Translations.current match {
      case t: JsTranslations => t.cookieName
      case _                 => Translations.DefaultCookieName
    }
    dom.document.cookie =
      s"${cookieName}=${URIUtils.encodeURIComponent(locale)}; path=/; max-age=31536000; SameSite=Lax"
    dom.window.location.reload()
  }

  /** Structure injected by the MiniServer. */
  @js.native
  private trait Payload extends js.Object {
    val locale: String                  = js.native
    val available: js.Array[String]     = js.native
    val cookie: js.UndefOr[String]      = js.native
    val messages: js.Dictionary[String] = js.native
  }

  private case class JsTranslations(payload: Payload) extends Translations {
    override val locale: String         = payload.locale
    override val available: Seq[String] = payload.available.toSeq
    val cookieName: String              = payload.cookie.getOrElse(Translations.DefaultCookieName)

    private val messages = payload.messages

    override def lookup(key: String): Option[String] = messages.get(key)
  }
}
