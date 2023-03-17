package kreuzberg

import kreuzberg.dom.ScalaJsElement
import kreuzberg.util.Stateful

type AssemblyResult[+R] = Stateful[AssemblyState, Assembly[R]]

type NodeResult[T, R] = Stateful[AssemblyState, ComponentNode[T, R]]

trait RuntimeContext {
  def jsElement: ScalaJsElement

  def jump(componentId: ComponentId): RuntimeContext
}

type RuntimeProvider[+R] = RuntimeContext => R

// Imperative often used components

type SimpleComponentBase = imperative.SimpleComponentBase

type SimpleContext = imperative.SimpleContext
