package kreuzberg

import kreuzberg.util.Stateful
import kreuzberg.Component
import scala.language.implicitConversions

import kreuzberg.dom.ScalaJsElement
trait ComponentDsl {
  implicit def htmlToAssemblyResult(in: Html): AssemblyResult[Unit] = {
    Stateful.pure(Assembly(in))
  }

  implicit def assemblyToAssemblyResult[R](assembly: Assembly[R]): AssemblyResult[R] = {
    Stateful.pure(assembly)
  }

  def namedChild[R, T <: Component.Aux[R]](
      name: String,
      value: T
  ): Stateful[AssemblyState, ComponentNode[R, T]] = {
    Assembler.assembleNamedChild(name, value)
  }

  def anonymousChild[R, T <: Component.Aux[R]](
      component: T
  ): Stateful[AssemblyState, ComponentNode[R, T]] = {
    Assembler.assembleWithNewId(component)
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

  case class RepBuilder[R, T <: Component.Aux[R]](rep: ComponentNode[R, T]) {
    def apply[E](f: T => Event[E]): EventSource[E] = EventSource.ComponentEvent(f(rep.component), Some(rep.id))
  }

  def from[R, T <: Component.Aux[R]](rep: ComponentNode[R, T]): RepBuilder[R, T] = RepBuilder(rep)
}
