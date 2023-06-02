package kreuzberg

/** Provided context for assembling operations. */
trait AssemblerContext extends ServiceRepository {

  /** Returns the value of a model. */
  def value[M](model: Model[M]): M

}

object AssemblerContext {
  def empty: Empty = new Empty

  /** An Empty assembler context. */
  class Empty extends AssemblerContext {
    override def value[M](model: Model[M]): M = model.initialValue()

    override def service[S](using provider: Provider[S]): S = {
      provider.create(using this)
    }
  }
}
