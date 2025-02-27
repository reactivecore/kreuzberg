package kreuzberg.extras
import kreuzberg.scalatags.all._
import kreuzberg.scalatags._

private[extras] object ScalaTagHelpers {

  /** Select the first non empty value */
  inline def firstNonEmpty(a: String, b: String): String = {
    if (a.isEmpty) {
      b
    } else {
      a
    }
  }

  /** Emit CSS-Classes if non empty */
  inline def cssClasses(classes: String): Modifier = {
    if (classes.isEmpty) {
      ()
    } else {
      cls := classes
    }
  }

  /** Emits CSS-Classes for an overriding or default set. */
  inline def cssClassesWithOverride(`override`: String, defaultClasses: String): Modifier = {
    cssClasses(firstNonEmpty(`override`, defaultClasses))
  }
}
