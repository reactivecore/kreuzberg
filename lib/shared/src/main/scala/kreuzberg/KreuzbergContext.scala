package kreuzberg

import org.scalajs.dom.Element

import scala.concurrent.ExecutionContext

/** Main Kreuzberg Context - Responsible retrieving state and scheduling changes. */
trait KreuzbergContext extends ModelValueProvider with ServiceRepository with ExecutionContext with Changer

object KreuzbergContext {

  /** Empty Context (for Tests) */
  object empty extends KreuzbergContext {
    override def updateModel[T](model: Model[T], updateFn: T => T): Unit = {}

    override def triggerChannel[T](channel: Channel[T], value: T): Unit = {}

    override def locate(identifier: Identifier): Element = throw new IllegalStateException("Empty Context")

    override def call(callback: () => Unit): Unit = {}

    override def execute(runnable: Runnable): Unit = {}

    override def reportFailure(cause: Throwable): Unit = {}

    override def value[M](model: Subscribeable[M]): M = model.initial(using this)

    override def serviceOption[S](using snp: ServiceNameProvider[S]): Option[S] = None
  }

  /** Combines different parts into one Context */
  class Compound(
      modelValueProvider: ModelValueProvider,
      serviceRepository: ServiceRepository,
      executionContext: ExecutionContext,
      changer: Changer
  ) extends KreuzbergContext {
    override def updateModel[T](model: Model[T], updateFn: T => T): Unit = changer.updateModel(model, updateFn)

    override def triggerChannel[T](channel: Channel[T], value: T): Unit = changer.triggerChannel(channel, value)

    override def locate(identifier: Identifier): Element = changer.locate(identifier)

    override def call(callback: () => Unit): Unit = changer.call(callback)

    override def execute(runnable: Runnable): Unit = executionContext.execute(runnable)

    override def reportFailure(cause: Throwable): Unit = executionContext.reportFailure(cause)

    override def value[M](model: Subscribeable[M]): M = modelValueProvider.value(model)

    override def serviceOption[S](using snp: ServiceNameProvider[S]): Option[S] = serviceRepository.serviceOption[S]
  }

}
