package kreuzberg

/** Helpers for building imperative Components using [[SimpleContext]] */
trait SimpleContextDsl extends ComponentDsl {
  self: Component =>

  /** Subscribe some model and read at the same time. */
  protected def subscribe[M](model: Subscribeable[M])(using c: SimpleContext): M = {
    model match {
      case Model.Constant(value) => value
      case _                     =>
        c.addSubscription(model)
        c.value(model)
    }
  }

  /** Add a child service. */
  protected def addService(service: HeadlessComponent, other: HeadlessComponent*)(using c: SimpleContext): Unit = {
    c.addService(service)
    other.foreach(c.addService)
  }

  /** Add an event binding. */
  protected def add(binding0: EventBinding, others: EventBinding*)(using c: SimpleContext): Unit = {
    c.addEventBinding(binding0)
    others.foreach(c.addEventBinding)
  }
}
