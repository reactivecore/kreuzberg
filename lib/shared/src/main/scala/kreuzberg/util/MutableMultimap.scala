package kreuzberg.util

import scala.collection.mutable

class MutableMultimap[K, V] {
  private val backend = mutable.Map[K, mutable.ArrayBuffer[V]]()

  def add(key: K, value: V): Unit = {
    backend.get(key) match {
      case None          =>
        backend += key -> mutable.ArrayBuffer(value)
      case Some(present) =>
        present.append(value)
    }
  }

  def containsKey(key: K): Boolean = {
    backend.contains(key)
  }

  def clear(): Unit = {
    backend.clear()
  }

  def isEmpty: Boolean = {
    backend.isEmpty
  }

  def foreachKey(key: K)(f: V => Unit): Unit = {
    backend.get(key) match {
      case None          =>
      // exit
      case Some(present) =>
        present.foreach(f)
    }
  }

  def sizeForKey(key: K): Int = {
    backend.get(key) match {
      case Some(values) => values.size
      case None         => 0
    }
  }

  def toSeq: Seq[(K, V)] = (for {
    key   <- backend.keys
    value <- backend(key)
  } yield (key, value)).toSeq

  def keys: Seq[K] = backend.keys.toSeq

  def filterValuesInPlace(f: V => Boolean): Unit = {
    val isEmptyCollector = mutable.ArrayBuffer[K]()
    backend.foreach { case (key, values) =>
      values.filterInPlace(f)
      if (values.isEmpty) {
        isEmptyCollector += key
      }
    }
    backend.subtractAll(isEmptyCollector)
  }

  def filterKeysInPlace(f: K => Boolean): Unit = {
    backend.filterInPlace((p, _) => f(p))
  }
}
