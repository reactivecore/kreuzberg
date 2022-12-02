package kreuzberg.scalatags
import kreuzberg.{Html, TreeNode, ComponentId}
import kreuzberg.imperative.PlaceholderState

import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.text.Builder

import java.io.Writer
import scala.language.implicitConversions

/** Adapts ScalaTags to HTML. */
case class ScalaTagsHtml(tag: TypedTag[String]) extends Html {
  override def withId(id: ComponentId): Html = {
    copy(
      tag = tag(data.id := id.id)
    )
  }

  override def addInner(inner: Seq[Html]): Html = {
    val mapped = inner.map {
      case ScalaTagsHtml(wrapped) => wrapped : Frag
      case other                  => ScalaTagsHtmlEmbed(other) : Frag
    }
    ScalaTagsHtml(tag(mapped))
  }

  override def placeholders: Iterable[TreeNode] = {
    PlaceholderTag.collectFrom(tag).map(_.node)
  }

  override def render(sb: StringBuilder): Unit = {
    tag.writeTo(new Writer() {
      override def write(cbuf: Array[Char], off: Int, len: Int): Unit = {
        sb.appendAll(cbuf, off, len)
      }

      override def flush(): Unit = {
        // empty
      }

      override def close(): Unit = {
        // empty
      }
    })
  }

  override def renderToString(): String = {
    val result = super.renderToString()
    PlaceholderState.clear()
    result
  }
}

object ScalaTagsHtml {
  implicit def fromScalaTags(inner: TypedTag[String]): ScalaTagsHtml = ScalaTagsHtml(inner)
}

/** Wraps HTML inside ScalaTags. */
case class ScalaTagsHtmlEmbed(
    html: Html
) extends scalatags.text.Frag {
  override def render: String = html.renderToString()

  override def applyTo(t: Builder): Unit = {
    t.addChild(this)
  }

  override def writeTo(strb: Writer): Unit = {
    // TODO: Can we optimize that?
    strb.write(render)
  }
}
