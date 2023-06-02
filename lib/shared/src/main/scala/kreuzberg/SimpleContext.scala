package kreuzberg

/** A simple of AssemblerContext which also collects event bindings / event subscriptions */
class SimpleContext(underlying: AssemblerContext) extends AssemblerContext {
  private val _eventBindings = Vector.newBuilder[EventBinding]
  private val _subscriptions = Vector.newBuilder[Model[_]]

  override def value[M](model: Model[M]): M = underlying.value(model)

  override def service[S](using provider: Provider[S]): S = underlying.service[S]

  def addEventBinding(binding: EventBinding): Unit = {
    _eventBindings += binding
  }

  def addSubscription(model: Model[_]): Unit = {
    _subscriptions += model
  }

  def eventBindings(): Vector[EventBinding] = _eventBindings.result()
  def subscriptions(): Vector[Model[_]]     = _subscriptions.result()
}
