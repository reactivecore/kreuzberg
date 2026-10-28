package org.scalajs.dom

// Only methods which are used by Kreuzberg

trait Event {
  def `type`: String

  def preventDefault(): Unit
}

/** Modifier keys of keyboard and mouse events. */
trait ModifierKeyEvent {
  def altKey: Boolean
  def ctrlKey: Boolean
  def shiftKey: Boolean
  def metaKey: Boolean
}

trait KeyboardEvent extends Event with ModifierKeyEvent {
  def key: String
  def code: String
}

trait MouseEvent extends Event with ModifierKeyEvent {
  def button: Int
  def clientX: Double
  def clientY: Double
}

trait Element {}

trait Node {
  def addEventListener[T <: Event](`type`: String, listener: T => ?, useCapture: Boolean = false): Unit
}
