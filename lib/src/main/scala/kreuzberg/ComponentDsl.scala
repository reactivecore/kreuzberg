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

  def namedChild[T: Assembler](name: String, value: T): Stateful[AssemblyState, Rep[T]] = {
    Assembler[T].assembleNamedChild(name, value)
  }

  def anonymousChild[T: Assembler](value: T): Stateful[AssemblyState, Rep[T]] = {
    Assembler[T].assembleWithNewId(value)
  }

  def subscribe[T](model: Model[T]): Stateful[AssemblyState, T] = {
    Stateful { state =>
      state.subscribe(model)
    }
  }

  def read[T](model: Model[T]): Stateful[AssemblyState, T] = {
    Stateful.get(_.readValue(model))
  }

  case class JsStateBuilder[J <: org.scalajs.dom.Element]() {
    def get[T](f: J => T): StateGetter.JsRepresentationState[J, T] = StateGetter.JsRepresentationState(f)
  }

  def js[J <: org.scalajs.dom.Element] = JsStateBuilder[J]()

  case class RepBuilder[T](rep: Rep[T]) {
    def apply[E](f: T => Event[E]): EventSource[E] = EventSource.RepEvent(rep, f(rep.value))
  }

  def from[T](rep: Rep[T]): RepBuilder[T] = RepBuilder(rep)
}
