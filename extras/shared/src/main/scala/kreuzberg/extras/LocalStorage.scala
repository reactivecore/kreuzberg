package kreuzberg.extras

import kreuzberg.*
import kreuzberg.extras.LocalStorage.Backend

import scala.util.Try

/**
 * Encapsulates a model, which is backed by local storage. Note: You need to have a Backend Service.
 */
case class LocalStorage[M](initial: M, key: String, serializer: M => String, deserializer: String => M)
    extends HeadlessComponentBase {

  /** The model contains the current value. */
  val model = Model.create(readValue())

  override def assemble(using context: AssemblerContext): HeadlessAssembly = {
    val current = read(model)
    HeadlessAssembly(
      handlers = Vector(
        EventSource.Assembled.handle { _ =>
          writeValue(current)
        }
      ),
      subscriptions = Vector(model)
    )
  }

  private def readValue()(using serviceRepository: ServiceRepository): M = {
    serviceRepository
      .service[Backend]
      .get(key)
      .flatMap { serialized =>
        Try(deserializer(serialized)).toOption
      }
      .getOrElse(initial)
  }

  private def writeValue(value: M)(using serviceRepository: ServiceRepository): Unit = {
    serviceRepository
      .service[Backend]
      .set(key, serializer(value))
  }
}

object LocalStorage {

  /** Necessary backend for LocalStorage. */
  trait Backend derives ServiceNameProvider {

    /** Save local Storage. */
    def set(key: String, value: String): Unit

    /** Load Local Storage. */
    def get(key: String): Option[String]
  }
}
