package kreuzberg

import kreuzberg.dom.ScalaJsElement
import kreuzberg.util.Stateful
import scala.language.implicitConversions

type AssemblyResult = Stateful[AssemblyState, Assembly]

object AssemblyResult {
  implicit def fromHtml(html: Html): AssemblyResult = {
    Stateful.pure(Assembly(html))
  }
}

type NodeResult[T <: Component] = Stateful[AssemblyState, ComponentNode[T]]

type TreeNodeResult = Stateful[AssemblyState, TreeNode]

// Imperative often used components

type SimpleComponentBase = imperative.SimpleComponentBase

type SimpleContext = imperative.SimpleContext

/** Something which can be embedded into HTML. */
type HtmlEmbedding = Component | Html
