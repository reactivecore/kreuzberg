package kreuzberg

/** Provides values for models. */
trait ModelValueProvider {

  /** Returns the value of a model. */
  def value[M](model: Subscribeable[M]): M

}

object ModelValueProvider {
  object empty extends ModelValueProvider {
    override def value[M](model: Subscribeable[M]): M = model.initial
  }
}
