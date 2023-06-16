package kreuzberg

/** Provides values for models. */
trait ModelValueProvider {

  /** Returns the value of a model. */
  def value[M](model: Subscribeable[M]): M

}

object ModelValueProvider {
  def empty: Empty = new Empty

  class Empty extends ModelValueProvider {
    override def value[M](model: Subscribeable[M]): M = model.initial()
  }
}

/** Provided context for assembling operations. */
trait AssemblerContext extends ModelValueProvider with ServiceRepository

object AssemblerContext {
  def empty: Empty = new Empty

  /** An Empty assembler context. */
  class Empty extends ModelValueProvider.Empty with AssemblerContext {
    override def service[S](using provider: Provider[S]): S = {
      provider.create(using this)
    }
  }
}
