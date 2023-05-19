package kreuzberg

import kreuzberg.util.Stateful
import kreuzberg.Component
import kreuzberg.dom.ScalaJsEvent
import scala.language.implicitConversions

import kreuzberg.dom.ScalaJsElement
trait ComponentDsl {
  self: Component =>

  implicit def htmlToAssemblyResult(in: Html): AssemblyResult[Unit] = {
    Stateful.pure(Assembly(in))
  }

  implicit def assemblyToAssemblyResult[R](assembly: Assembly[R]): AssemblyResult[R] = {
    Stateful.pure(assembly)
  }

  def subscribe[T](model: Model[T]): Stateful[AssemblyState, T] = {
    Stateful { state =>
      state.subscribe(self.id, model)
    }
  }

  def provide[T: Provider]: Stateful[AssemblyState, T] = {
    Stateful(_.provide)
  }

  def read[T](model: Model[T]): Stateful[AssemblyState, T] = {
    Stateful.get(_.readValue(model))
  }

  def jsEvent(name: String, preventDefault: Boolean = false, capture: Boolean = false): JsEvent[ScalaJsEvent] =
    JsEvent(Some(id), name, preventDefault, capture)

  def windowEvent(name: String, preventDefault: Boolean = false, capture: Boolean = false): JsEvent[ScalaJsEvent] =
    JsEvent(None, name, preventDefault, capture)

  def from[E](jsEvent: JsEvent[E]): EventSource.Js[E] = EventSource.Js(jsEvent)
}
