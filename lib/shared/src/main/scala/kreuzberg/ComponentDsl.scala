package kreuzberg

import kreuzberg.util.Stateful

import scala.language.implicitConversions

import kreuzberg.dom.ScalaJsElement
trait ComponentDsl {
  implicit def htmlToAssemblyResult(in: Html): AssemblyResult[Unit] = {
    Stateful.pure(Assembly(in))
  }

  implicit def assemblyToAssemblyResult[R](assembly: Assembly[R]): AssemblyResult[R] = {
    Stateful.pure(assembly)
  }

  def namedChild[T](name: String, value: T)(
      implicit a: Assembler[T]
  ): Stateful[AssemblyState, ComponentNode[T, a.RuntimeNode]] = {
    a.assembleNamedChild(name, value)
  }

  def anonymousChild[T](
      value: T
  )(implicit a: Assembler[T]): Stateful[AssemblyState, ComponentNode[T, a.RuntimeNode]] = {
    a.assembleWithNewId(value)
  }

  def subscribe[T](model: Model[T]): Stateful[AssemblyState, T] = {
    Stateful { state =>
      state.subscribe(model)
    }
  }

  def provide[T: Provider]: Stateful[AssemblyState, T] = {
    Stateful(_.provide)
  }

  def read[T](model: Model[T]): Stateful[AssemblyState, T] = {
    Stateful.get(_.readValue(model))
  }

  case class RepBuilder[T](rep: ComponentNode[T, _]) {
    def apply[E](f: T => Event[E]): EventSource[E] = EventSource.ComponentEvent(f(rep.value), Some(rep.id))
  }

  def from[T](rep: ComponentNode[T, _]): RepBuilder[T] = RepBuilder(rep)
}
