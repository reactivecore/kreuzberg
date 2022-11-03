package kreuzberg

import org.scalajs.dom.Element as JsElement
import scalatags.Text.TypedTag

/** Represents an assembled node in a tree. */
sealed trait TreeNode {
  def id: ComponentId
  def assembly: Assembly

  /** Returns children nodes. */
  def children: Vector[TreeNode] = assembly.nodes
}

/** A Representation of the component. */
case class ComponentNode[T](
    id: ComponentId,
    value: T,
    assembly: Assembly,
    assembler: Assembler[T]
) extends TreeNode
