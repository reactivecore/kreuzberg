package kreuzberg

import java.util.concurrent.ConcurrentHashMap

/** Contains unique services identified by their ID. */
trait ServiceRepository {

  /** Returns a service, given a provider. */
  def service[S](using provider: Provider[S]): S
}

/**
 * A Type class providing something using Assembly State. Used for dependency injecting shared models.
 */
trait Provider[T] {

  /** Unique name for the service */
  def name: String

  /** Create a new instance of the service. */
  def create(using serviceRepository: ServiceRepository): T
}
