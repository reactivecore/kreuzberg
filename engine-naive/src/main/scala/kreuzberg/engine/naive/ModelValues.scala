package kreuzberg.engine.naive

import kreuzberg.*

/** Simple holder for model values. */
private[kreuzberg] case class ModelValues(
    modelValues: Map[Identifier, Any] = Map.empty
) {
  self =>

  def withModelValue[M](id: Identifier, value: M): ModelValues = {
    copy(
      modelValues = modelValues + (id -> value)
    )
  }

  /** Returns the value of a model. */
  def value[M](model: Model[M])(using ServiceRepository): M = {
    modelValues.get(model.id) match {
      case Some(ok) => ok.asInstanceOf[M]
      case None     =>
        model.initial
    }
  }

  /** Convert to a ModelValueProvider. */
  def toModelValueProvider(using ServiceRepository): ModelValueProvider = {
    new ModelValueProvider {
      override def modelValue[M](model: Model[M]): M = {
        self.value(model)
      }
    }
  }
}
