package kreuzberg.dom

// Dummy Implementations for JVM

trait ScalaJsEvent {
  def preventDefault(): Unit
}

trait ScalaJsNode {
  def addEventListener[T <: ScalaJsEvent](`type`: String, listener: T => _, useCapture: Boolean = false): Unit
}

trait ScalaJsElement {
  def querySelector(selectors: String): ScalaJsElement
}

trait ScalaJsInput extends ScalaJsElement {

  /** Text Input of input field */
  var value: String
}

trait ScalaJsTextArea extends ScalaJsElement {

  /** Text Input of text area */
  def value: String
}

trait ScalaJsDataTransfer {
  var effectAllowed: String
  def setData(format: String, data: String): Unit
  def getData(format: String): String
}

trait ScalaJsDragEvent extends ScalaJsEvent {
  def dataTransfer: ScalaJsDataTransfer
}
