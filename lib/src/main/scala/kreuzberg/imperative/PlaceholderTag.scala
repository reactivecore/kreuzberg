package kreuzberg.imperative

import kreuzberg._
import scalatags.Text.TypedTag
import scalatags.Text.all.Modifier
import scalatags.text.Builder

import scala.collection.mutable

/** Wraps a TreeNode inside an HTML Node. */
case class PlaceholderTag(node: TreeNode) extends Modifier {
  override def applyTo(t: Builder): Unit = {
    PlaceholderState.get(node.id).applyTo(t)
  }
}

object PlaceholderTag {

  /** Collect extended tags inside html */
  def collectFrom(html: Html): Vector[PlaceholderTag] = {
    val result = Vector.newBuilder[PlaceholderTag]

    def walk(s: TypedTag[String]): Unit = {
      for {
        modifierBlock <- s.modifiers
        modifier      <- modifierBlock
      } {
        modifier match {
          case e: PlaceholderTag   =>
            result.addOne(e)
          case s: TypedTag[String] =>
            walk(s)
          case ignore              =>
            println(s"Ignoring tag: ${ignore} Class: ${ignore.getClass}")
        }
      }
    }

    walk(html)
    result.result()
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
