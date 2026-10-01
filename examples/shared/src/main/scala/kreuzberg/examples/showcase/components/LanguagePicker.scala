package kreuzberg.examples.showcase.components

import kreuzberg.*
import kreuzberg.i18n.{Translations, tr}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import org.scalajs.dom.html.Select

/** Selects the language, which is stored in a cookie by reloading the page. */
case object LanguagePicker extends SimpleComponentBase {

  val languages = Seq(
    "en" -> "English",
    "de" -> "Deutsch",
    "fr" -> "Français"
  )

  override type DomElement = Select

  override def assemble(using sc: SimpleContext): Html = {
    val current = Translations.current.locale
    add(
      onChange.handle { _ =>
        Translations.switchLocale(selectedValue.read())
      }
    )
    select(aria.label := tr("language.label"))(
      languages.map { case (code, name) =>
        option(value := code, if (code == current) selected)(name)
      }
    )
  }

  def onChange      = jsEvent("change")
  def selectedValue = jsProperty(_.value, (r, v) => r.value = v)
}
