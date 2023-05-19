package kreuzberg.imperative

import kreuzberg.*
import kreuzberg.dom.ScalaJsEvent

/** Helpers for building imperative Components using [[SimpleContext]] */
trait SimpleContextDsl {
  self: Component =>

  protected def subscribe[M](model: Model[M])(implicit c: SimpleContext): M = {
    c.transformFn(_.subscribe(self.id, model))
  }

  protected def read[M](model: Model[M])(implicit c: SimpleContext): M = {
    c.get(_.readValue(model))
  }

  protected def provide[T: Provider](implicit c: SimpleContext): T = {
    c.transformFn(_.provide[T])
  }

  def from[E](jsEvent: JsEvent[E]): EventSource.Js[E] = EventSource.Js(jsEvent)

  def from[E](channel: Channel[E]): EventSource.ChannelSource[E] = EventSource.ChannelSource(channel)

  /** Declare a Javascript event. */
  def jsEvent(name: String, preventDefault: Boolean = false, capture: Boolean = false): JsEvent[ScalaJsEvent] =
    JsEvent(Some(id), name, preventDefault, capture)

  /** Declare a Window JS Event. */
  def windowEvent(name: String, preventDefault: Boolean = false, capture: Boolean = false): JsEvent[ScalaJsEvent] =
    JsEvent(None, name, preventDefault, capture)

  import scala.language.implicitConversions

  implicit def htmlToAssembly(in: Html): Assembly[Unit] = {
    Assembly(in)
  }

  def html[T](in: T)(implicit f: T => Html): Html = f(in)

  extension (html: Html) {
    def withRuntime[T](f: RuntimeContext ?=> T): (Html, RuntimeProvider[T]) = {
      html -> { rc =>
        f(using rc)
      }
    }
  }

  protected def add(binding0: EventBinding, others: EventBinding*)(implicit c: SimpleContext): Unit = {
    c.addEventBinding(binding0)
    others.foreach(c.addEventBinding)
  }
}
