package org.scalajs.dom

// Only methods which are used by Kreuzberg

trait Event {
  def preventDefault(): Unit
}

trait Element {}

trait Node {
  def addEventListener[T <: Event](`type`: String, listener: T => ?, useCapture: Boolean = false): Unit
}
