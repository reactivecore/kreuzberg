package kreuzberg

import kreuzberg.util.Stateful

import scala.language.implicitConversions

trait ComponentDsl {
  implicit def htmlToAssembleResult(in: Html): AssemblyResult = {
    Stateful.pure(Assembly(in))
  }

  implicit def assemblyToAssemblyResult(assembly: Assembly): AssemblyResult = {
    Stateful.pure(assembly)
  }

  def namedChild[T: Assembler](name: String, value: T): Stateful[AssemblyState, ComponentNode[T]] = {
    Assembler[T].assembleNamedChild(name, value)
  }

  def anonymousChild[T: Assembler](value: T): Stateful[AssemblyState, ComponentNode[T]] = {
    Assembler[T].assembleWithNewId(value)
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

  case class JsStateBuilder[J <: ScalaJsElement]() {
    def get[T](f: J => T): StateGetter.JsRepresentationState[J, T] = StateGetter.JsRepresentationState(f)
  }

  def js[J <: ScalaJsElement] = JsStateBuilder[J]()

  case class RepBuilder[T](rep: ComponentNode[T]) {
    def apply[E](f: T => Event[E]): EventSource[E] = EventSource.RepEvent(rep, f(rep.value))
  }

  def from[T](rep: ComponentNode[T]): RepBuilder[T] = RepBuilder(rep)
}
