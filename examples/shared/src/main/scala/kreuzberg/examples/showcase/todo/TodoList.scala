package kreuzberg.examples.showcase.todo

import kreuzberg.*

/** A Simple TODO List. */
case class TodoList(
    elements: Seq[String]
) {
  def append(s: String): TodoList = {
    copy(
      elements = elements :+ s
    )
  }
}
