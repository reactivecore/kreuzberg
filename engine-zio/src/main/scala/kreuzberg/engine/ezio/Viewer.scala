package kreuzberg.engine.ezio

import kreuzberg.*
import kreuzberg.dom.*
import zio.{Task, ZIO}

import scala.util.control.NonFatal

/** Responsible for drawing componnent trees. */
class Viewer(rootElement: ScalaJsElement) {

  /** Redraw root node */
  def drawRoot(node: TreeNode): Task[Unit] = {
    ZIO.attempt {
      val html = node.render()
      rootElement.innerHTML = html.toString
    }
  }

  /** Redraw one node. */
  def updateNode(node: TreeNode): Task[Unit] = {
    val htmlEffect = ZIO.attempt {
      val result = node.render()
      result
    }
    for {
      html    <- htmlEffect
      current <- findElement(node.id)
      _       <- {
        ZIO.attempt {
          current.outerHTML = html.toString
        }
      }
    } yield {
      ()
    }
  }

  /** Find an element for a given Component ID. */
  def findElement(id: ComponentId): Task[ScalaJsElement] = {
    ZIO
      .attempt {
        findElementUnsafe(id)
      }
      .logError(s"Could not find element ${id}")
  }

  def findElementUnsafe(id: ComponentId): ScalaJsElement = {
    val element = rootElement
      .querySelector(s"[data-id=\"${id.id}\"]")
    if (element == null) {
      throw new RuntimeException(s"Could not find element with id ${id}")
    }
    element
  }
}
