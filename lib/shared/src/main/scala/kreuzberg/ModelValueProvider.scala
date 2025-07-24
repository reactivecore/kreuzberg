package kreuzberg

/** Provides values for models. */
trait ModelValueProvider {

  /** Returns the value of a model. */
  def value[M](model: Subscribeable[M]): M

}
