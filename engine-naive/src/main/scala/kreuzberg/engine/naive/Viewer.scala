package kreuzberg.engine.naive

import kreuzberg.imperative.PlaceholderState
import kreuzberg.{Logger, TreeNode}
import kreuzberg.*
import kreuzberg.dom.*
import scala.util.control.NonFatal

/** Responsible for drawing componnent trees. */
class Viewer(rootElement: ScalaJsElement) {

  /** Redraw root node */
  def drawRoot(node: TreeNode): Unit = {
    val html = node.render()
    rootElement.innerHTML = html.toString
    PlaceholderState.clear()
  }

  /** Redraw one node. */
  def updateNode(node: TreeNode): Unit = {
    val html    = node.render()
    val current = findElement(node.id)
    current.outerHTML = html.toString
    PlaceholderState.clear()
  }

  /** Find an element for a given Component ID. */
  def findElement(id: ComponentId): ScalaJsElement = {
    try {
      rootElement
        .querySelector(s"[data-id=\"${id.id}\"]")
    } catch {
      case NonFatal(e) =>
        Logger.warn(s"Could not find ${id}: ${e.getMessage}")
        throw e
    }
  }
}
