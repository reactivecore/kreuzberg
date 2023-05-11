package kreuzberg.scalatags
import kreuzberg.{ComponentId, FlatHtmlBuilder, Html, TreeNode}
import kreuzberg.util.SimpleThreadLocal
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.text.Builder

import java.io.Writer
import scala.collection.mutable
import scala.language.implicitConversions
import java.io.StringWriter

/** Adapts ScalaTags to HTML. */
case class ScalaTagsHtml(tag: TypedTag[String]) extends Html {
  override def withId(id: ComponentId): Html = {
    copy(
      tag = tag(data.id := id.id)
    )
  }

  def addComment(c: String): Html = {
    ScalaTagsHtml(
      tag.copy(
        modifiers = tag.modifiers :+ List(CommentTag(c))
      )
    )
  }

  override def embeddedNodes: Iterable[TreeNode] = {
    ScalaTagsEmbedding.collectFrom(tag).flatMap {
      case ScalaTagsHtmlEmbedding(html)         => html.embeddedNodes
      case ScalaTagsTreeNodeEmbedding(treeNode) => Seq(treeNode)
    }
  }

  override def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit = {
    val writer: Writer = new Writer() {
      override def write(cbuf: Array[Char], off: Int, len: Int): Unit = flatHtmlBuilder.add(cbuf, off, len)

      override def flush(): Unit = {}

      override def close(): Unit = {}
    }
    FlatHtmlBuilder.withFlatHtmlBuilder(flatHtmlBuilder) {
      tag.writeTo(writer)
    }
  }
}

object ScalaTagsHtml {
  implicit def fromScalaTags(inner: TypedTag[String]): ScalaTagsHtml = ScalaTagsHtml(inner)
}
