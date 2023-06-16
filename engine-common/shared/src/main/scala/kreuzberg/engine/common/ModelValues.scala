package kreuzberg.engine.common
import kreuzberg.{Identifier, Model, ModelValueProvider, Subscribeable}

/** Simple holder for model values. */
case class ModelValues(
    modelValues: Map[Identifier, Any] = Map.empty
) extends ModelValueProvider {

  def withModelValue[M](id: Identifier, value: M): ModelValues = {
    copy(
      modelValues = modelValues + (id -> value)
    )
  }

  /** Read a value without subscribing it (doesn't make component dependent from it) */
  override def value[M](model: Subscribeable[M]): M = {
    model match {
      case model: Model[_]              => modelValues.getOrElse(model.id, model.initialValue()).asInstanceOf[M]
      case Model.Mapped(underlying, fn) => fn(value(underlying))
    }
  }
}
