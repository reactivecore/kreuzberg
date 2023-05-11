package kreuzberg

import kreuzberg.dom.ScalaJsElement
import kreuzberg.util.Stateful
import scala.language.implicitConversions

type AssemblyResult[+R] = Stateful[AssemblyState, Assembly[R]]

object AssemblyResult {
  implicit def fromHtml(html: Html): AssemblyResult[Unit] = {
    Stateful.pure(Assembly(html))
  }
}

type NodeResult[R, T <: Component.Aux[R]] = Stateful[AssemblyState, ComponentNode[R, T]]

type TreeNodeResult = Stateful[AssemblyState, TreeNode]

trait RuntimeContext {
  def jsElement: ScalaJsElement

  def jump(componentId: ComponentId): RuntimeContext
}

type RuntimeProvider[+R] = RuntimeContext => R

// Imperative often used components

type SimpleComponentBase = imperative.SimpleComponentBase

type SimpleContext = imperative.SimpleContext

/** Something which can be embedded into HTML. */
type HtmlEmbedding = TreeNode | Html
