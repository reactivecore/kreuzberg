package kreuzberg

import kreuzberg.RuntimeState.JsProperty
import org.scalajs.dom.Element

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext

/** Context for imperative Event Handlers */
@implicitNotFound("HandlerContext not found, are you inside an EventHandler?")
trait HandlerContext extends ModelValueProvider with ServiceRepository with ExecutionContext {

  /** Update model, existing to new state */
  def updateModel[T](model: Model[T], updateFn: T => T): Unit

  /** Trigger a channel. */
  def triggerChannel[T](channel: Channel[T], value: T): Unit

  /** Call another Event sink */
  def triggerSink[E](sink: EventSink[E], value: E): Unit

  /** Locate an Element. */
  def locate(identifier: Identifier): org.scalajs.dom.Element
  
  /** Call something (stateful) on next iteration. */
  def call(callback: HandlerContext ?=> Unit): Unit
}
