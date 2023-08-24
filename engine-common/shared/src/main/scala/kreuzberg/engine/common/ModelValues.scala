package kreuzberg.engine.common
import kreuzberg.{Identifier, Model, ModelValueProvider, ServiceRepository, Subscribeable}

/** Simple holder for model values. */
case class ModelValues(
    modelValues: Map[Identifier, Any] = Map.empty
) {
  self =>

  def withModelValue[M](id: Identifier, value: M): ModelValues = {
    copy(
      modelValues = modelValues + (id -> value)
    )
  }

  /** Returns the value of a model. */
  def value[M](model: Subscribeable[M])(using ServiceRepository): M = {
    model match {
      case model: Model[M]              =>
        modelValues.get(model.id) match {
          case Some(ok) => ok.asInstanceOf[M]
          case None     =>
            model.initial
        }
      case Model.Mapped(underlying, fn) => fn(value(underlying))
    }
  }

  /** Convert to a ModelValueProvider. */
  def toModelValueProvider(using ServiceRepository): ModelValueProvider = {
    new ModelValueProvider {
      override def value[M](model: Subscribeable[M]): M = {
        self.value(model)
      }
    }
  }
}
