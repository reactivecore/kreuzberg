package kreuzberg

import kreuzberg.Component
import kreuzberg.dom.ScalaJsEvent
import scala.language.implicitConversions

import kreuzberg.dom.ScalaJsElement
trait ComponentDsl {
  self: Component =>

  protected implicit def htmlToAssemblyResult(in: Html): Assembly = {
    Assembly(in)
  }

  /** Implicitly convert JS Events into event sources. */
  protected implicit def from[E](jsEvent: JsEvent[E]): EventSource.Js[E] = EventSource.Js(jsEvent)

  /** Implicitly convert Channels into event sources. */
  protected implicit def from[E](channel: Channel[E]): EventSource.ChannelSource[E] = EventSource.ChannelSource(channel)

  /** Declare a Javascript event. */
  protected def jsEvent(
      name: String,
      preventDefault: Boolean = false,
      capture: Boolean = false
  ): JsEvent[ScalaJsEvent] =
    JsEvent(Some(id), name, preventDefault, capture)

  /** Declare a Window JS Event. */
  protected def windowEvent(
      name: String,
      preventDefault: Boolean = false,
      capture: Boolean = false
  ): JsEvent[ScalaJsEvent] =
    JsEvent(None, name, preventDefault, capture)

  /** Declares a js runtime state. */
  protected def jsState[T](f: DomElement => T): RuntimeState.JsRuntimeState[DomElement, T] = {
    RuntimeState.JsRuntimeState(id, f)
  }

  protected def provide[T: Provider](using c: ServiceRepository): T = {
    c.service[T]
  }

  protected def read[M](model: Model[M])(using c: AssemblerContext): M = {
    c.value(model)
  }
}
