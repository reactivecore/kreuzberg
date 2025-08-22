package kreuzberg

import scala.quoted.{Expr, Quotes, Type}

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
  def get[T: ServiceNameProvider]: T = {
    KreuzbergContext.get().sr.service[T]
  }

  object empty extends ServiceRepository {
    override def serviceOption[S](using snp: ServiceNameProvider[S]): Option[S] = {
      None
    }
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
  case class Impl[T](name: String) extends ServiceNameProvider[T]

  def create[T](withName: String): ServiceNameProvider[T] = Impl(withName)

  inline def derived[T]: ServiceNameProvider[T] = {
    Impl(typeName[T])
  }

  private inline def typeName[T]: String = {
    ${ typeNameImpl[T] }
  }

  private def typeNameImpl[T](using types: Type[T], quotes: Quotes): Expr[String] = {
    Expr(Type.show[T])
  }
}

/** Exception thrown if a service is not found. */
case class ServiceNotFoundException(serviceName: String) extends RuntimeException(s"Service ${serviceName} not found")
