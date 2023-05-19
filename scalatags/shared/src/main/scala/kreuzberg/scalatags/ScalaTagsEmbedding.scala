package kreuzberg.scalatags

import kreuzberg.util.SimpleThreadLocal
import kreuzberg.{Component, FlatHtmlBuilder, Html, TreeNode}
import scalatags.Text.TypedTag
import scalatags.text.Builder

import java.io.Writer
import scala.collection.mutable

/** Things which we embed into Scalatags. */
sealed trait ScalaTagsEmbedding extends scalatags.text.Frag

object ScalaTagsEmbedding {

  /** Collect extended tags inside html */
  private[scalatags] def collectFrom(html: TypedTag[String]): Vector[ScalaTagsEmbedding] = {
    /*
    There is no simple way to deconstruct the HTML and it is also not designed for that use case.
    Hower we can just render it, and collect all placeholders using ThreadLocal variables
     */
    ScalaTagsEmbeddingCollector.begin()
    html.toString
    ScalaTagsEmbeddingCollector.end()
  }
}

/** Helper for collecting embedded [ScalaTagsEmbedding] elements. */
private[scalatags] object ScalaTagsEmbeddingCollector {
  private val isActive  = new SimpleThreadLocal(false)
  private val collector = new SimpleThreadLocal(mutable.ArrayBuffer[ScalaTagsEmbedding]())

  /** Check if we are in the collecting phase, then do nothing. */
  def collectingPhase(embed: ScalaTagsEmbedding): Boolean = {
    if (isActive.get()) {
      collector.get() += embed
      true
    } else {
      false
    }
  }

  /** Start collecting embedded elements. */
  def begin(): Unit = {
    isActive.set(true)
  }

  /** End collecting embedded elements. */
  def end(): Vector[ScalaTagsEmbedding] = {
    isActive.set(false)
    val result = collector.get().toVector
    collector.get().clear()
    result
  }
}

/** Wraps HTML inside ScalaTags. */
case class ScalaTagsHtmlEmbedding(
    html: Html
) extends ScalaTagsEmbedding {
  override def render: String = {
    html.renderToString()
  }

  override def applyTo(t: Builder): Unit = {
    if (!ScalaTagsEmbeddingCollector.collectingPhase(this)) {
      t.addChild(this)
    }
  }

  override def writeTo(strb: Writer): Unit = {
    FlatHtmlBuilder.current match {
      case Some(flatBuilder) =>
        html.flatToBuilder(flatBuilder)
      case None              =>
        strb.write(render)
    }
  }
}

/** Wraps a Component into the ScalaTags */
case class ScalaTagsComponentEmbedding(component: Component) extends ScalaTagsEmbedding {
  override def render: String = {
    s"<component id=\"${component.id}\"/>"
  }

  override def applyTo(t: Builder): Unit = {
    if (!ScalaTagsEmbeddingCollector.collectingPhase(this)) {
      t.addChild(this)
    }
  }

  override def writeTo(strb: Writer): Unit = {
    FlatHtmlBuilder.current match {
      case Some(flatBuilder) =>
        flatBuilder.addPlaceholder(component.id)
      case None              =>
        strb.write(render)
    }
  }
}
