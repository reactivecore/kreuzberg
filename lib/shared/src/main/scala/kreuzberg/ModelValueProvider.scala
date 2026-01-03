package kreuzberg

/** Provides values for models. */
trait ModelValueProvider {

  def value[M](subscribeable: Subscribeable[M]): M = subscribeable match {
    case model: Model[M] @unchecked   => modelValue(model)
    case Model.Mapped(underlying, fn) => fn(value(underlying))
    case Model.Constant(value)        => value
  }

  /** Returns the value of a model. */
  def modelValue[M](model: Model[M]): M

}

object ModelValueProvider {
  object empty extends ModelValueProvider {
    override def modelValue[M](model: Model[M]): M = model.initial
  }
}
