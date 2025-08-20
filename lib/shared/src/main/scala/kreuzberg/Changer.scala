package kreuzberg

import org.scalajs.dom.Element

/** Responsible for doing mutable changes. */
private[kreuzberg] trait Changer {

  /** Update model, existing to new state */
  def updateModel[T](model: Model[T], updateFn: T => T): Unit

  /** Trigger a channel. */
  def triggerChannel[T](channel: Channel[T], value: T): Unit

  /** Locate an Element. */
  def locate(identifier: Identifier): org.scalajs.dom.Element

  /** Call something (stateful) on next iteration. */
  def call(callback: () => Unit): Unit
}

private[kreuzberg] object Changer {
  object empty extends Changer {
    override def updateModel[T](model: Model[T], updateFn: T => T): Unit = {}

    override def triggerChannel[T](channel: Channel[T], value: T): Unit = {}

    override def locate(identifier: Identifier): Element = throw new IllegalStateException("Empty Changer")

    override def call(callback: () => Unit): Unit = ()
  }
}
