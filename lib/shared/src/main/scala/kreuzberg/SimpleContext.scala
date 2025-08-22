package kreuzberg

/** A simple helper for collecting event bindings / subscriptions / services within [[SimpleComponentBase]] */
class SimpleContext {
  private val _eventBindings = Vector.newBuilder[EventBinding[?]]
  private val _subscriptions = Vector.newBuilder[Subscribeable[?]]
  private val _services      = Vector.newBuilder[HeadlessComponent]

  def addEventBinding(binding: EventBinding[?]): Unit = {
    _eventBindings += binding
  }

  def addSubscription(model: Subscribeable[?]): Unit = {
    _subscriptions += model
  }

  def addService(service: HeadlessComponent): Unit = {
    _services += service
  }

  def eventBindings(): Vector[EventBinding[?]]  = _eventBindings.result()
  def subscriptions(): Vector[Subscribeable[?]] = _subscriptions.result()
  def services(): Vector[HeadlessComponent]     = _services.result()
}
