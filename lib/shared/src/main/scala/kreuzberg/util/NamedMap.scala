package kreuzberg.util

/** A Map from some key to some named map. */
case class NamedMap[K, V](
    underlying: Map[K, Map[String, V]] = Map.empty[K, Map[String, V]]
) extends AnyVal {

  def get(key: K, name: String): Option[V] = {
    underlying.get(key).flatMap(_.get(name))
  }

  def +(value: ((K, String), V)): NamedMap[K, V] = {
    withValue(value._1._1, value._1._2, value._2)
  }

  def withValue(key: K, name: String, value: V): NamedMap[K, V] = {
    val updatedMap = underlying.getOrElse(key, Map.empty) + (name -> value)
    NamedMap(underlying + (key -> updatedMap))
  }

  def filterKeys(f: K => Boolean): NamedMap[K, V] = {
    NamedMap(underlying.view.filterKeys(f).toMap)
  }

  def filter(f: (K, String, V) => Boolean): NamedMap[K, V] = {
    val filtered = for {
      (key, map)    <- underlying.view
      (name, value) <- map
      if f(key, name, value)
    } yield (key, name, value)
    NamedMap.build(filtered)
  }

  def values: Iterable[V] = {
    for {
      subMap     <- underlying.view
      (_, value) <- subMap._2.view
    } yield value
  }

  def size: Int = {
    underlying.foldLeft(0) { case (current, (_, values)) =>
      current + values.size
    }
  }
}

object NamedMap {
  def empty[K, V] = NamedMap[K, V]()

  def build[K, V](values: Iterable[(K, String, V)]): NamedMap[K, V] = {
    NamedMap(
      values
        .groupBy(_._1)
        .view
        .mapValues { values =>
          values.map { case (_, name, value) => (name, value) }.toMap
        }
        .toMap
    )
  }
}
