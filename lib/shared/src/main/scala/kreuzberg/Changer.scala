package kreuzberg

/** Responsible for doing mutable changes. */
trait Changer {

  /** Update model, existing to new state */
  def updateModel[T](model: Model[T], updateFn: T => T): Unit

  /** Trigger a channel. */
  def triggerChannel[T](channel: Channel[T], value: T): Unit

  /** Locate an Element. */
  def locate(identifier: Identifier): org.scalajs.dom.Element

  /** Call something (stateful) on next iteration. */
  def call(callback: () => Unit): Unit
}
