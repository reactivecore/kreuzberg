package kreuzberg.engine.ezio.utils

/** Immutable ListMap based upon List. */
opaque type MultiListMap[K, V] = Map[K, List[V]]

object MultiListMap {
  def apply[K, V](in: Map[K, List[V]] = Map.empty[K, List[V]]): MultiListMap[K, V] = in

  def from[K, V](in: Iterable[(K, V)]): MultiListMap[K,V] = {
    in.groupBy(_._1).view.mapValues(_.map(_._2).toList).toMap
  }

  def empty[K, V] = apply[K, V]()

  extension [K, V](m: MultiListMap[K, V]) {
    def add(key: K, value: V): MultiListMap[K, V] = {
      m.get(key) match {
        case None         => m + (key -> List(value))
        case Some(values) => m + (key -> (value :: values))
      }
    }

    def get(key: K): List[V] = {
      m.getOrElse(key, Nil)
    }

    def remove(key: K): (List[V], MultiListMap[K, V]) = {
      m.get(key) match {
        case None           => (Nil, m)
        case Some(existing) => existing -> m.removed(key)
      }
    }

    /** Filter by key */
    def filterKeys(f: K => Boolean): MultiListMap[K, V] = {
      m.view.filterKeys(f).toMap
    }

    /** Partition by Key */
    def partitionKeys(f: K => Boolean): (MultiListMap[K, V], MultiListMap[K, V]) = {
      val (first, second) = m.partition { values => f(values._1) }
      (first, second)
    }

    def partition(f: (K, V) => Boolean): (MultiListMap[K, V], MultiListMap[K, V]) = {
      val (first, second) = asIterable.partition(x => f(x._1, x._2))
      from(first) -> from(second)
    }

    def asIterable: Iterable[(K, V)] = {
      for {
        (k, values) <- m
        value       <- values
      } yield (k, value)
    }

    /** Returns all values. */
    def values: Iterable[V] = {
      m.values.view.flatten
    }
  }
}
