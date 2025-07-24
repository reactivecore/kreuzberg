package kreuzberg.engine.naive

import kreuzberg.{
  Channel,
  Identifier,
  KreuzbergContext,
  KreuzbergExecutionContext,
  Model,
  ModelValueProvider,
  ServiceNameProvider,
  ServiceRepository,
  Subscribeable
}
import org.scalajs.dom.Element

/** Context for naive Implementation. */
private[kreuzberg] class ContextImpl(
    browserDrawer: BrowserDrawer,
    eventManager: EventManager,
    serviceRepository: ServiceRepository,
    models: ModelValueProvider
) extends KreuzbergContext
    with KreuzbergExecutionContext {
  override def updateModel[T](model: Model[T], updateFn: T => T): Unit = {
    eventManager.updateModel(model, updateFn)
  }

  override def triggerChannel[T](channel: Channel[T], value: T): Unit = {
    eventManager.triggerChannel(channel, value)
  }

  override def locate(identifier: Identifier): Element = {
    browserDrawer.findElement(identifier)
  }

  override def call(callback: () => Unit): Unit = eventManager.call(callback)

  override def value[M](model: Subscribeable[M]): M = {
    models.value(model)
  }

  override def serviceOption[S](using snp: ServiceNameProvider[S]): Option[S] = serviceRepository.serviceOption[S]
}
