package kreuzberg

import kreuzberg.RuntimeState.JsProperty
import kreuzberg.dom.ScalaJsElement

import scala.concurrent.ExecutionContext
import scala.ref.WeakReference

/** Context for imperative Event Handlers */
trait HandlerContext extends ModelValueProvider with ServiceRepository with ExecutionContext {

  /** Issue a model change. */
  def setModel[T](model: Model[T], value: T): Unit

  /** Update model, existing to new state */
  def updateModel[T](model: Model[T], updateFn: T => T): Unit

  /** Trigger a channel. */
  def triggerChannel[T](channel: Channel[T], value: T): Unit

  /** Call another Event sink */
  def triggerSink[E](sink: EventSink[E], value: E): Unit

  /** Read some JavaScript state. */
  def state[T](state: RuntimeState[T]): T

  /** Set some JavaScript property. */
  def setProperty[D <: ScalaJsElement, T](property: JsProperty[D, T], value: T): Unit
}
