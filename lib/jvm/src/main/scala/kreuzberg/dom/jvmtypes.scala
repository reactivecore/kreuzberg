package kreuzberg.dom

// Dummy Implementations for JVM

trait ScalaJsEvent()

trait ScalaJsNode {
  def addEventListener[T <: ScalaJsEvent](`type`: String, listener: T => _, useCapture: Boolean = false): Unit
}

trait ScalaJsElement {
  def querySelector(selectors: String): ScalaJsElement
}

trait ScalaJsInput extends ScalaJsElement {

  /** Text Input of input field */
  def value: String
}
