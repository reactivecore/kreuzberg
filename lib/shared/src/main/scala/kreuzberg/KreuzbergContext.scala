package kreuzberg

import scala.concurrent.ExecutionContext

/** Main Kreuzberg Context - Responsible retrieving state and scheduling changes. */
private[kreuzberg] trait KreuzbergContext {

  /** Access to model values. */
  def mvp: ModelValueProvider

  /** Access to service repository */
  def sr: ServiceRepository

  /** Access to ExecutionContext */
  def ec: ExecutionContext

  /** Access to mutable changes. */
  def changer: Changer

  /** Use this context within an asynchronously claled method. */
  inline def use[T](f: => T): T = KreuzbergContext.threadLocal.withInstance(this)(f)
}

private[kreuzberg] object KreuzbergContext {

  /** Combines different parts into one Context */
  class Compound(
      override val mvp: ModelValueProvider,
      override val sr: ServiceRepository,
      override val changer: Changer
  ) extends KreuzbergContext {
    override lazy val ec: ExecutionContext = new KreuzbergExecutionContext(changer)
  }

  /** Empty Context (for Tests) */
  object empty extends Compound(ModelValueProvider.empty, ServiceRepository.empty, Changer.empty)

  private[kreuzberg] val threadLocal = new SimpleThreadLocal[KreuzbergContext](null)

  def get(): KreuzbergContext = {
    val value = threadLocal.get()
    if (value == null) {
      throw new IllegalStateException(
        s"""KreuzbergContext is only available within Components and Kreuzberg provided Callbacks.
           |You should use the ExecutionContext of a component to run a JavaScript callback.
           |""".stripMargin
      )
    }
    value
  }
}
