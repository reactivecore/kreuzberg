package kreuzberg

import kreuzberg.util.Stateful

/** IDs for Models. */
case class ModelId(id: Int) extends AnyVal {
  def inc: ModelId = copy(id = id + 1)
}

/** Represents a model */
case class Model[M](ownerId: ComponentId, name: String, id: ModelId)

object Model {
  def make[M](name: String, initialValue: M): Stateful[AssemblyState, Model[M]] = {
    Stateful(_.withModel(name, initialValue))
  }
}
