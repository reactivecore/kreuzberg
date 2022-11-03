package kreuzberg

import kreuzberg.imperative.PlaceholderState
import org.scalajs.dom.{Element, document, Event as JsEvent}
import scalatags.Text.all.*

import scala.util.control.NonFatal

/** Responsible for drawing componnent trees. */
class Viewer(rootElement: Element) {
  import Viewer.*

  /** Redraw root node */
  def drawRoot(node: TreeNode): Unit = {
    val html = render(node)
    rootElement.innerHTML = html.toString
    PlaceholderState.clear()
  }

  /** Redraw one node. */
  def updateNode(node: TreeNode): Unit = {
    val html    = render(node)
    val current = findElement(node.id)
    current.outerHTML = html.toString
    PlaceholderState.clear()
  }

  /** Find an element for a given Component ID. */
  def findElement(id: ComponentId): Element = {
    try {
      rootElement
        .querySelector(s"[data-id=\"${id.id}\"]")
    } catch {
      case NonFatal(e) =>
        println(s"Could not find ${id}: ${e.getMessage}")
        throw e
    }
  }
}

object Viewer {

  /** Render a single node into HTML */
  def render(node: TreeNode): Html = {
    node match
      case rep: ComponentNode[_] => renderRep(rep)
  }

  private def renderRep[X](rep: ComponentNode[X]): Html = {
    rep.assembly match
      case p: Assembly.Pure      =>
        p.html(data.id := rep.id.id)
      case c: Assembly.Container => {
        val renderedChildren = c.nodes.map(render)
        val pureHtml         = c.renderer(renderedChildren)
        val html             = pureHtml(data.id := rep.id.id)
        html
      }
  }
}
