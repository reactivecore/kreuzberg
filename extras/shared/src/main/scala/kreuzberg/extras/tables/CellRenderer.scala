package kreuzberg.extras.tables

import kreuzberg.Html
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/** Type class rendering a cell. */
trait CellRenderer[T] {

  /** Render the field. */
  def render(value: T): Html

  /** Overriding default `<td>` Classes */
  def classOverride(value: T): String = ""

  def contraMap[U](f: U => T): CellRenderer[U] = value => render(f(value))

  /** Override classes value for `td`. */
  def withClassOverride(classes: String): CellRenderer[T] = {
    val self = this
    new CellRenderer[T] {
      override def render(value: T): Html = self.render(value)

      override def classOverride(value: T): String = classes
    }
  }
}

object CellRenderer {

  /** Default Renderer */
  given default[T]: CellRenderer[T] = value => all.span(value.toString)
}
