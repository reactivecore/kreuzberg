package kreuzberg

import java.io.Writer
import scala.collection.mutable
import scala.language.implicitConversions

trait Html {

  /** Appends the data.id attribut to the HTML Code. */
  def withId(id: ComponentId): Html

  /** Add some comment to the HTML Comment. */
  def addComment(c: String): Html

  /** Add more HTML at the end of the tag. */
  def addInner(inner: Seq[Html]): Html

  /** Returns all embedded components within the HTML Code. */
  def placeholders: Iterable[TreeNode]

  /** Render the HTML. */
  def render(sb: StringBuilder): Unit

  /** Render the HTML to a String. */
  def renderToString(): String = {
    val sb = new StringBuilder()
    render(sb)
    sb.toString()
  }

  override def toString: String = {
    renderToString()
  }
}

object Html {
  implicit def treeNodeToHtml(treeNode: TreeNode): Html = TreeNodePlaceholder(treeNode)
}

/** Wraps a TreeNode inside Html. */
case class TreeNodePlaceholder(treeNode: TreeNode) extends Html {
  def withId(id: ComponentId): Html = {
    treeNode.render().withId(id)
  }

  def addComment(c: String): Html = {
    treeNode.render().addComment(c)
  }

  def addInner(inner: Seq[Html]): Html = {
    treeNode.render().addInner(inner)
  }

  def placeholders: Iterable[TreeNode] = {
    List(treeNode)
  }

  def render(sb: StringBuilder): Unit = {
    treeNode.render().render(sb)
  }
}
