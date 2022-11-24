package kreuzberg

/** Dummy Event for Scala JVM */
trait ScalaJsEvent()

/** Dummy Node. */
trait ScalaJsNode {
  def addEventListener[T <: ScalaJsEvent](`type`: String, listener: T => _, useCapture: Boolean = false): Unit
}

/** Dummy Element for Scala JVM */
trait ScalaJsElement {
  def querySelector(selectors: String): ScalaJsElement
}
