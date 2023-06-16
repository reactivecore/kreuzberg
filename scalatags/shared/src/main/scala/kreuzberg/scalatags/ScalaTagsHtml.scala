package kreuzberg.scalatags
import kreuzberg.{Component, FlatHtmlBuilder, Html, Identifier}
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.text.Builder

import java.io.Writer
import scala.collection.mutable
import scala.language.implicitConversions
import java.io.StringWriter

/** Adapts ScalaTags to HTML. */
case class ScalaTagsHtml(tag: TypedTag[String]) extends Html {
  override def withId(id: Identifier): Html = {
    copy(
      tag = tag(data.id := id.value)
    )
  }

  def addComment(c: String): Html = {
    ScalaTagsHtml(
      tag.copy(
        modifiers = tag.modifiers :+ List(CommentTag(c))
      )
    )
  }

  override def embeddedComponents: Iterable[Component] = {
    ScalaTagsEmbedding.collectFrom(tag).flatMap {
      case ScalaTagsHtmlEmbedding(html)   => html.embeddedComponents
      case ScalaTagsComponentEmbedding(c) => Seq(c)
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

  override def appendChild(html: Html): Html = {
    ScalaTagsHtml(
      tag(ScalaTagsHtml.maybeWrap(html))
    )
  }

  override def prependChild(html: Html): Html = {
    ScalaTagsHtml(
      tag.copy(
        modifiers = tag.modifiers :+ List(ScalaTagsHtml.maybeWrap(html))
      )
    )
  }
}

object ScalaTagsHtml {

  def maybeWrap(html: Html): Frag = {
    html match {
      case s: ScalaTagsHtml => s.tag
      case other            =>
        ScalaTagsHtmlEmbedding(html)
    }
  }

  implicit def fromScalaTags(inner: TypedTag[String]): ScalaTagsHtml = ScalaTagsHtml(inner)
}
