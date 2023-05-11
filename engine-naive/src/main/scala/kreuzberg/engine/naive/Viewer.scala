package kreuzberg.engine.naive

import kreuzberg.{Logger, TreeNode}
import kreuzberg.*
import kreuzberg.dom.*
import scala.util.control.NonFatal

/** Responsible for drawing componnent trees. */
class Viewer(rootElement: ScalaJsElement) {

  /** Redraw root node */
  def drawRoot(node: TreeNode): Unit = {
    val html = node.render()
    println(s"Generated HTML: ${html}")
    rootElement.innerHTML = html
  }

  /** Redraw one node. */
  def updateNode(node: TreeNode): Unit = {
    val html    = node.render()
    val current = findElement(node.id)
    current.outerHTML = html
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
