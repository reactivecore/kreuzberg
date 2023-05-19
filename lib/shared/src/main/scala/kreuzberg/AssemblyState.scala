package kreuzberg

import kreuzberg.util.{NamedMap, Stateful}

/**
 * @param subscribers
 *   contain model subscribers
 */
case class AssemblyState(
    modelValues: Map[Identifier, Any] = Map.empty,
    subscribers: Vector[(Identifier, Identifier)] = Vector.empty, // Convert to some multimap?
    services: NamedMap[Identifier, Any] = NamedMap.empty
) {

  def provide[T: Provider]: (AssemblyState, T) = implicitly[Provider[T]].provide(this)

  def withModelValue[M](id: Identifier, value: M): AssemblyState = {
    copy(
      modelValues = modelValues + (id -> value)
    )
  }

  def subscribe[T](ownId: Identifier, model: Model[T]): (AssemblyState, T) = {
    val value = readValue(model)
    copy(
      subscribers = subscribers :+ (model.id -> ownId)
    ) -> value
  }

  /** Read a value without subscribing it (doesn't make component dependent from it) */
  def readValue[T](model: Model[T]): T = {
    modelValues.getOrElse(model.id, model.initialValue()).asInstanceOf[T]
  }

  /** Create a root service. */
  def rootService[T](name: String, initializer: () => T): (AssemblyState, T) = {
    serviceWithOwner(Identifier.RootComponent, name, initializer)
  }

  private def serviceWithOwner[T](ownerId: Identifier, name: String, initializer: () => T): (AssemblyState, T) = {
    services.get(ownerId, name) match {
      case None          =>
        val created = initializer()
        copy(
          services = services.withValue(ownerId, name, created)
        ) -> created
      case Some(service) => this -> service.asInstanceOf[T]
    }
  }
}
