package kreuzberg

import kreuzberg.util.NamedMap

/** @param ownId
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
    path: List[ComponentId] = List(ComponentId(0)),
    nextId: ComponentId = ComponentId(1),
    nextModelId: ModelId = ModelId(1),
    nextBusId: BusId = BusId(1),
    children: NamedMap[ComponentId, ComponentId] = NamedMap.empty,
    models: NamedMap[ComponentId, Model[_]] = NamedMap.empty,
    modelValues: Map[ModelId, Any] = Map.empty,
    busses: NamedMap[ComponentId, Bus[_]] = NamedMap.empty,
    subscribers: Vector[(ModelId, ComponentId)] = Vector.empty // Convert to some multimap?
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
  def withModel[M](name: String, initial: M): (AssemblyState, Model[M]) = {
    models.get(ownId, name) match {
      case Some(model) =>
        (this, model.asInstanceOf[Model[M]])
      case None        =>
        val m         = Model[M](ownId, name, nextModelId)
        val nextState = copy(
          models = models.withValue(ownId, name, m),
          modelValues = modelValues + (nextModelId -> initial),
          nextModelId = nextModelId.inc
        )
        (nextState, m)
    }
  }

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

  def withBus[T](name: String): (AssemblyState, Bus[T]) = {
    busses.get(ownId, name) match {
      case Some(bus) =>
        this -> bus.asInstanceOf[Bus[T]]
      case None      =>
        val bus       = Bus[T](ownId, name, nextBusId)
        val nextState = copy(
          busses = busses.withValue(ownId, name, bus),
          nextBusId = nextBusId.inc
        )
        (nextState, bus)
    }
  }
}
