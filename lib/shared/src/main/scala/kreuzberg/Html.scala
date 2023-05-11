package kreuzberg

import java.io.Writer
import scala.collection.mutable
import scala.language.implicitConversions

trait Html {

  /** Appends the data.id attribut to the HTML Code. */
  def withId(id: ComponentId): Html

  /** Add some comment to the HTML Comment. */
  def addComment(c: String): Html

  /** Returns all embedded components within the HTML Code. */
  def embeddedNodes: Iterable[TreeNode]

  /** Render the HTML. */
  def render(sb: StringBuilder): Unit = {
    val placeholderMap = embeddedNodes.map { treeNode =>
      treeNode.id -> treeNode
    }.toMap

    val nodeRender: (ComponentId, StringBuilder) => Unit = { (id, builder) =>
      placeholderMap(id).renderTo(builder)
    }

    flat().render(sb, nodeRender)
  }

  /** Render the HTML to a String. */
  def renderToString(): String = {
    val sb = new StringBuilder()
    render(sb)
    sb.toString()
  }

  override def toString: String = {
    renderToString()
  }

  /** Convert to a flat HTML representation. */
  def flat(): FlatHtml = {
    val builder = FlatHtmlBuilder()
    flatToBuilder(builder)
    builder.result()
  }

  /** Serializes into a FlatHtmlBuilder. */
  def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit
}
