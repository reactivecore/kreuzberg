package kreuzberg

import kreuzberg.Component

import scala.language.implicitConversions

/** Common Methods for building [[Component]]-Implementations */
trait ContextDsl {

  /** Retrieves something from a [[ServiceRepository]] (usually an [[KreuzbergContext]]) */
  protected def provide[T: ServiceNameProvider]: T = {
    KreuzbergContext.get().sr.service[T]
  }

  /** Read the value of a model */
  protected def read[M](model: Subscribeable[M]): M = {
    model.read()
  }
}

trait ComponentDsl extends ContextDsl {
  self: Component =>

  protected implicit def htmlToAssemblyResult(in: Html): Assembly = {
    Assembly(in)
  }

  /** Declare a Javascript event. */
  protected def jsEvent(
      name: String,
      preventDefault: Boolean = false,
      capture: Boolean = false
  ): JsEvent =
    JsEvent(Some(id), name, preventDefault, capture)

  /** Declare a Window JS Event. */
  protected def windowEvent(
      name: String,
      preventDefault: Boolean = false,
      capture: Boolean = false
  ): JsEvent =
    JsEvent(None, name, preventDefault, capture)

  /** Declares a js runtime state. */
  protected def jsState[T](f: DomElement => T): RuntimeState.JsRuntimeState[DomElement, T] = {
    RuntimeState.JsRuntimeState(id, f)
  }

  /** Declares a js runtime property. */
  protected def jsProperty[T](
      getter: DomElement => T,
      setter: (DomElement, T) => Unit
  ): RuntimeState.JsProperty[DomElement, T] = {
    RuntimeState.JsProperty(id, getter, setter)
  }
}
