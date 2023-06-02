package kreuzberg.engine.common
import kreuzberg.{Identifier, Model}

/** Simple holder for model values. */
case class ModelValues(
    modelValues: Map[Identifier, Any] = Map.empty
) {

  def withModelValue[M](id: Identifier, value: M): ModelValues = {
    copy(
      modelValues = modelValues + (id -> value)
    )
  }

  /** Read a value without subscribing it (doesn't make component dependent from it) */
  def readValue[T](model: Model[T]): T = {
    modelValues.getOrElse(model.id, model.initialValue()).asInstanceOf[T]
  }
}
