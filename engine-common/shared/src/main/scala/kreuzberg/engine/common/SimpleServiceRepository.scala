package kreuzberg.engine.common

import kreuzberg.{Provider, ServiceRepository}

import java.util.concurrent.ConcurrentHashMap

class SimpleServiceRepository extends ServiceRepository {
  private val services = new ConcurrentHashMap[String, Any]()

  override def service[S](using provider: Provider[S]): S = {
    // Note: this can lead to double creations(), but not in practice as JavaScript is single threaded
    Option(services.get(provider.name)) match {
      case Some(existing) => existing.asInstanceOf[S]
      case None           =>
        val candidate = provider.create(using this)
        Option(services.putIfAbsent(provider.name, candidate)) match {
          case Some(hasAlready) =>
            hasAlready.asInstanceOf[S]
          case None             =>
            candidate
        }
    }
  }
}
