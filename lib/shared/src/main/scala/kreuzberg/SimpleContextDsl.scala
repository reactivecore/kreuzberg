package kreuzberg

/** Helpers for building imperative Components using [[SimpleContext]] */
trait SimpleContextDsl extends ComponentDsl {
  self: Component =>

  /** Subscribe some model and read at the same time. */
  protected def subscribe[M](model: Subscribeable[M])(using c: SimpleContext): M = {
    c.addSubscription(model)
    c.value(model)
  }

  /** Add an event binding. */
  protected def add(binding0: EventBinding, others: EventBinding*)(using c: SimpleContext): Unit = {
    c.addEventBinding(binding0)
    others.foreach(c.addEventBinding)
  }
}
