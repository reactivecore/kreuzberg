package kreuzberg.imperative

import kreuzberg.*
import kreuzberg.util.SimpleThreadLocal
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.text
import scalatags.text.Builder

import scala.collection.mutable

/** Wraps a TreeNode inside an HTML Node. */
case class PlaceholderTag(node: TreeNode) extends Modifier {
  override def applyTo(t: Builder): Unit = {
    if (!PlaceholderCollector.collectingPhase(this)) {
      PlaceholderState.get(node.id).applyTo(t)
    }
  }
}

object PlaceholderTag {

  /** Collect extended tags inside html */
  def collectFrom(html: Html): Vector[PlaceholderTag] = {
    /*
    There is no simple way to deconstruct the HTML and it is also not designed for that use case.
    Hower we can just render it, and collect all placeholders using ThreadLocal variable.s
     */
    PlaceholderCollector.begin()
    html.toString
    PlaceholderCollector.end()
  }
}

/** Threadlocal helper for using [[PlaceHolderState]] */
object PlaceholderState {
  private val renderings: ThreadLocal[mutable.Map[ComponentId, Html]] =
    new ThreadLocal[mutable.Map[ComponentId, Html]] {
      override def initialValue(): mutable.Map[ComponentId, Html] = {
        mutable.Map()
      }
    }

  def get(componentId: ComponentId): Html = {
    renderings.get().apply(componentId)
  }

  def set(componentId: ComponentId, html: Html) = {
    renderings.get().addOne(componentId, html)
  }

  def clear(): Unit = {
    renderings.get().clear()
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
