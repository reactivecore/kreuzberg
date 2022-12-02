package kreuzberg.scalatags

import kreuzberg.{Html, TreeNode}
import kreuzberg.imperative.PlaceholderState
import kreuzberg.util.SimpleThreadLocal
import scalatags.Text.all._
import scalatags.text.Builder
import scalatags.Text.TypedTag

import scala.collection.mutable

/** Wraps a TreeNode inside an HTML Node. */
case class PlaceholderTag(node: TreeNode) extends Modifier {
  override def applyTo(t: Builder): Unit = {
    if (!PlaceholderCollector.collectingPhase(this)) {
      val html = PlaceholderState.maybeGet(node.id).getOrElse(node.render())
      html match {
        case ScalaTagsHtml(inner) => inner.applyTo(t)
        case otherHtml            => ScalaTagsHtmlEmbed(otherHtml).applyTo(t)
      }
    }
  }
}

object PlaceholderTag {

  /** Collect extended tags inside html */
  def collectFrom(html: TypedTag[String]): Vector[PlaceholderTag] = {
    /*
    There is no simple way to deconstruct the HTML and it is also not designed for that use case.
    Hower we can just render it, and collect all placeholders using ThreadLocal variable.s
     */
    PlaceholderCollector.begin()
    html.toString
    PlaceholderCollector.end()
  }
}

private object PlaceholderCollector {
  private val isActive  = new SimpleThreadLocal(false)
  private val collector = new SimpleThreadLocal(mutable.ArrayBuffer[PlaceholderTag]())

  /** Check if we are in the collecting phase, then do nothing. */
  def collectingPhase(placeholder: PlaceholderTag): Boolean = {
    if (isActive.get()) {
      collector.get() += placeholder
      true
    } else {
      false
    }
  }

  def begin(): Unit = {
    isActive.set(true)
  }

  def end(): Vector[PlaceholderTag] = {
    isActive.set(false)
    val result = collector.get().toVector
    collector.get().clear()
    result
  }
}
