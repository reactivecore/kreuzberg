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

  def clear(): Unit = {
    backend.clear()
  }

  def foreachKey(key: K)(f: V => Unit): Unit = {
    backend.get(key) match {
      case None          =>
      // exit
      case Some(present) =>
        present.foreach(f)
    }
  }
}
