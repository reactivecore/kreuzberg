package kreuzberg.engine.common

import kreuzberg.*
import kreuzberg.dom.*
import kreuzberg.engine.common.UpdatePath.Change
import kreuzberg.engine.common.TreeNode

import scala.util.control.NonFatal

/** Responsible for drawing component trees. */
class BrowserDrawer(rootElement: ScalaJsElement) {

  /** Redraw root node */
  def drawRoot(node: TreeNode): Unit = {
    val html = node.render()
    rootElement.innerHTML = html
  }

  /** Redraw one node. */
  def updateNode(node: TreeNode): Unit = {
    val html    = node.render()
    val current = findElement(node.id)
    current.outerHTML = html
  }

  /** Draw an update path. */
  def drawUpdatePath(updatePath: UpdatePath): Unit = {
    updatePath.changes.foreach(drawChange)
  }

  /** Draw a single change. */
  def drawChange(change: Change): Unit = {
    change match
      case Change.Rerender(node)              =>
        Logger.trace(s"Rerendering ${node.id}")
        updateNode(node)
      case Change.AppendHtml(id, node, html)  =>
        Logger.trace(s"Appending node ${id} with html ${html}")
        appendNode(id, html)
      case Change.PrependHtml(id, node, html) =>
        Logger.trace(s"Prependiung node ${id} with html ${html}")
        prependNode(id, html)
  }

  /** Append some node without destroying listeners. */
  def appendNode(id: Identifier, html: String): Unit = {
    val current = findElement(id)
    current.insertAdjacentHTML("beforeend", html)
  }

  /** Prepend some node without destroying listeners */
  def prependNode(id: Identifier, html: String): Unit = {
    val current = findElement(id)
    current.insertAdjacentHTML("afterbegin", html)
  }

  /** Find an element for a given Component ID. */
  def findElement(id: Identifier): ScalaJsElement = {
    try {
      rootElement
        .querySelector(s"[data-id=\"${id.value}\"]")
    } catch {
      case NonFatal(e) =>
        Logger.warn(s"Could not find ${id}: ${e.getMessage}")
        throw e
    }
  }
}
