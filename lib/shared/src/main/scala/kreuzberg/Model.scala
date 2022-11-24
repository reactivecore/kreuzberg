package kreuzberg

import kreuzberg.util.Stateful

/** IDs for Models. */
opaque type ModelId = Int

object ModelId {

  def apply(id: Int = 1): ModelId = id

  extension (m: ModelId) {
    def inc: ModelId = m + 1

    def id: Int = m
  }
}

/** Represents a model */
case class Model[M](ownerId: ComponentId, name: String, id: ModelId)

object Model {
  def make[M](name: String, initialValue: => M): Stateful[AssemblyState, Model[M]] = {
    Stateful(_.withModel(name, initialValue))
  }

  /** Creates a root model */
  def makeRoot[M](name: String, initialValue: => M): Stateful[AssemblyState, Model[M]] = {
    Stateful(_.withRootModel(name, initialValue))
  }
}
