package kreuzberg

/** A simple of KreuzbergContext which also collects event bindings / event subscriptions */
class SimpleContext(underlying: KreuzbergContext)
    extends KreuzbergContext.Compound(
      modelValueProvider = underlying,
      serviceRepository = underlying,
      executionContext = underlying,
      changer = underlying
    ) {
  private val _eventBindings = Vector.newBuilder[EventBinding[?]]
  private val _subscriptions = Vector.newBuilder[Subscribeable[?]]
  private val _services      = Vector.newBuilder[HeadlessComponent]

  override def value[M](model: Subscribeable[M]): M = {
    underlying.value(model)
  }

  override def serviceOption[S](using snp: ServiceNameProvider[S]): Option[S] = underlying.serviceOption[S]

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
