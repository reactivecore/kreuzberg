package kreuzberg

/** Provides values for models. */
trait ModelValueProvider {

  /** Returns the value of a model. */
  def value[M](model: Subscribeable[M]): M

}

/** Provided context for assembling operations. */
trait AssemblerContext extends ModelValueProvider with ServiceRepository

object AssemblerContext {
  def empty: Empty = new Empty

  /** An Empty assembler context. */
  class Empty extends ModelValueProvider with AssemblerContext {
    override def value[M](model: Subscribeable[M]): M = model.initial(using this)

    override def serviceOption[S](using snp: ServiceNameProvider[S]): Option[S] = None
  }
}
