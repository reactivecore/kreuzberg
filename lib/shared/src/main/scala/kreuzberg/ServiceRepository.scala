package kreuzberg

/** Contains unique services identified by their Name. */
trait ServiceRepository {

  /** Returns a service, given it's name provider */
  @throws[ServiceNotFoundException]("If a service is not found.")
  def service[S](using snp: ServiceNameProvider[S]): S = serviceOption[S].getOrElse {
    throw ServiceNotFoundException(snp.name)
  }

  /** Returns a service if given. */
  def serviceOption[S](using snp: ServiceNameProvider[S]): Option[S]
}

object ServiceRepository {

  /** Returns a new [[ExtensibleServiceRepository]] */
  inline def extensible: ExtensibleServiceRepository = ExtensibleServiceRepository()

  /** Get something from the Service Repository. */
  @throws[ServiceNotFoundException]("If a service is not found")
  def get[T](using snp: ServiceNameProvider[T], repo: ServiceRepository): T = {
    repo.service
  }
}

/** Service Repository where it's possible to add services. */
case class ExtensibleServiceRepository(instances: Map[String, Any] = Map.empty) extends ServiceRepository {

  override def serviceOption[S](using snp: ServiceNameProvider[S]): Option[S] = {
    instances.get(snp.name).map(_.asInstanceOf[S])
  }

  /** Add some type to the repository */
  def add[T](value: T)(using snp: ServiceNameProvider[T]): ExtensibleServiceRepository = {
    copy(
      instances = instances + (snp.name -> value)
    )
  }
}

/** A Type class which provides names for services. */
trait ServiceNameProvider[T] {

  /** Unique name for the service */
  def name: String
}

object ServiceNameProvider {
  def create[T](withName: String): ServiceNameProvider[T] = new ServiceNameProvider[T] {
    override def name: String = withName
  }
}

/** Exception thrown if a service is not found. */
case class ServiceNotFoundException(serviceName: String) extends RuntimeException(s"Service ${serviceName} not found")
