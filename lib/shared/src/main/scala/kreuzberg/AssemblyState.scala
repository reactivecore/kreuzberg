package kreuzberg

import kreuzberg.AssemblyState.RootComponent
import kreuzberg.util.{NamedMap, Stateful}

/**
 * @param ownId
 *   own component id (will be stateful changed on children nodes)
 * @param nextId
 *   next id for components
 * @param nextModelId
 *   next id for models
 * @param children
 *   (owner -> name -> id) contains children relationship for named components
 * @param subscribers
 *   contain model subscribers
 */
case class AssemblyState(
    path: List[ComponentId] = List(AssemblyState.RootComponent),
    nextId: ComponentId = ComponentId(1),
    nextModelId: ModelId = ModelId(1),
    children: NamedMap[ComponentId, ComponentId] = NamedMap.empty,
    models: NamedMap[ComponentId, Model[_]] = NamedMap.empty,
    modelValues: Map[ModelId, Any] = Map.empty,
    subscribers: Vector[(ModelId, ComponentId)] = Vector.empty, // Convert to some multimap?
    services: NamedMap[ComponentId, Any] = NamedMap.empty
) {

  def generateId: (AssemblyState, ComponentId) = {
    val nextState = copy(nextId = nextId.inc)
    (nextState, nextId)
  }

  /** Push a component id during assembly */
  def pushId(id: ComponentId): AssemblyState = {
    copy(
      path = id :: path
    )
  }

  /** Pop a component id during assembly */
  def popId: AssemblyState = {
    copy(
      path = path.tail
    )
  }

  /** Returns current active id */
  def ownId: ComponentId = path.head

  /** Ensure an id for children. */
  def ensureChildren(name: String): (AssemblyState, ComponentId) = {
    children.get(ownId, name) match {
      case None        =>
        val (state1, id) = generateId
        val state2       = state1.copy(
          children = state1.children.withValue(ownId, name, id)
        )
        (state2, id)
      case Some(value) =>
        (this, value)
    }
  }

  /** Ensure existance of a named model. */
  def withModel[M](name: String, initial: => M): (AssemblyState, Model[M]) = {
    withModelOnId(ownId, name, initial)
  }

  /**
   * Ensures the existance of a model on the root object. Used by Providers.
   */
  def withRootModel[M](name: String, initial: => M): (AssemblyState, Model[M]) = {
    withModelOnId(AssemblyState.RootComponent, name, initial)
  }

  private def withModelOnId[M](owner: ComponentId, name: String, initial: => M): (AssemblyState, Model[M]) = {
    models.get(owner, name) match {
      case Some(model) =>
        (this, model.asInstanceOf[Model[M]])
      case None        =>
        val m         = Model[M](owner, name, nextModelId)
        val nextState = copy(
          models = models.withValue(owner, name, m),
          modelValues = modelValues + (nextModelId -> initial),
          nextModelId = nextModelId.inc
        )
        (nextState, m)
    }
  }

  def provide[T: Provider]: (AssemblyState, T) = implicitly[Provider[T]].provide(this)

  def withModelValue[M](id: ModelId, value: M): AssemblyState = {
    copy(
      modelValues = modelValues + (id -> value)
    )
  }

  def subscribe[T](model: Model[T]): (AssemblyState, T) = {
    val value = modelValues(model.id).asInstanceOf[T]
    copy(
      subscribers = subscribers :+ (model.id -> ownId)
    ) -> value
  }

  /** Read a value without subscribing it (doesn't make component dependent from it) */
  def readValue[T](model: Model[T]): T = {
    modelValues(model.id).asInstanceOf[T]
  }

  /** Create a service. */
  def service[T](name: String, initializer: () => T): (AssemblyState, T) = {
    serviceWithOwner(ownId, name, initializer)
  }

  /** Create a root service. */
  def rootService[T](name: String, initializer: () => T): (AssemblyState, T) = {
    serviceWithOwner(RootComponent, name, initializer)
  }

  private def serviceWithOwner[T](ownerId: ComponentId, name: String, initializer: () => T): (AssemblyState, T) = {
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

object AssemblyState {

  /** Anonymous root components, owner of root models. */
  val RootComponent = ComponentId(0)
}
