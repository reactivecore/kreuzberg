package kreuzberg.examples.showcase

import kreuzberg._

case class TodoList(
    elements: Seq[String]
) {
  def append(s: String): TodoList = {
    copy(
      elements = elements :+ s
    )
  }
}
