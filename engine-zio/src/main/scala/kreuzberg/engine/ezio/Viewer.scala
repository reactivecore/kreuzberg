package kreuzberg.engine.ezio

import kreuzberg.*
import kreuzberg.dom.*
import kreuzberg.imperative.PlaceholderState
import zio.{Task, ZIO}

import scala.util.control.NonFatal

/** Responsible for drawing componnent trees. */
class Viewer(rootElement: ScalaJsElement) {

  /** Redraw root node */
  def drawRoot(node: TreeNode): Task[Unit] = {
    ZIO.attempt {
      val html = node.render()
      rootElement.innerHTML = html.toString
      PlaceholderState.clear()
    }
  }

  /** Redraw one node. */
  def updateNode(node: TreeNode): Task[Unit] = {
    val htmlEffect = ZIO.attempt {
      val result = node.render()
      PlaceholderState.clear() // TODO: Remove Me after merging ticket branch
      result
    }
    for {
      html <- htmlEffect
      current <- findElement(node.id)
      _ <- {
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
        val element = rootElement
          .querySelector(s"[data-id=\"${id.id}\"]")
        if (element == null) {
          throw new RuntimeException(s"Could not find element with id ${id}")
        }
        element
      }
      .logError(s"Could not find element ${id}")
  }
}
