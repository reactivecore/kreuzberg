package kreuzberg.imperative
import kreuzberg.{ComponentId, Html}

import scala.collection.mutable

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

  def maybeGet(componentId: ComponentId): Option[Html] = {
    renderings.get().get(componentId)
  }

  def set(componentId: ComponentId, html: Html) = {
    renderings.get().addOne(componentId, html)
  }

  def clear(): Unit = {
    renderings.get().clear()
  }
}
