package kreuzberg.scalatags
import kreuzberg.{ComponentId, Html, TreeNode}
import kreuzberg.imperative.PlaceholderState
import kreuzberg.util.SimpleThreadLocal
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.text.Builder

import java.io.Writer
import scala.collection.mutable
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
      case ScalaTagsHtml(wrapped) => wrapped: Frag
      case other                  => ScalaTagsHtmlEmbed(other): Frag
    }
    ScalaTagsHtml(tag(mapped))
  }

  override def placeholders: Iterable[TreeNode] = {
    ScalaTagsHtmlEmbed.collectFrom(tag).flatMap(_.html.placeholders)
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
  override def render: String = {
    html.renderToString()
  }

  override def applyTo(t: Builder): Unit = {
    if (!ScalaTagsHtmlEmbedCollector.collectingPhase(this)) {
      t.addChild(this)
    }
  }

  override def writeTo(strb: Writer): Unit = {
    // TODO: Can we optimize that?
    strb.write(render)
  }
}

object ScalaTagsHtmlEmbed {

  /** Collect extended tags inside html */
  private[scalatags] def collectFrom(html: TypedTag[String]): Vector[ScalaTagsHtmlEmbed] = {
    /*
    There is no simple way to deconstruct the HTML and it is also not designed for that use case.
    Hower we can just render it, and collect all placeholders using ThreadLocal variable.s
     */
    ScalaTagsHtmlEmbedCollector.begin()
    html.toString
    ScalaTagsHtmlEmbedCollector.end()
  }
}

/** Helper for collecting embedded [ScalaTagsHtmlEmbed] elements. */
private object ScalaTagsHtmlEmbedCollector {
  private val isActive  = new SimpleThreadLocal(false)
  private val collector = new SimpleThreadLocal(mutable.ArrayBuffer[ScalaTagsHtmlEmbed]())

  /** Check if we are in the collecting phase, then do nothing. */
  def collectingPhase(embed: ScalaTagsHtmlEmbed): Boolean = {
    if (isActive.get()) {
      collector.get() += embed
      true
    } else {
      false
    }
  }

  def begin(): Unit = {
    isActive.set(true)
  }

  def end(): Vector[ScalaTagsHtmlEmbed] = {
    isActive.set(false)
    val result = collector.get().toVector
    collector.get().clear()
    result
  }
}
