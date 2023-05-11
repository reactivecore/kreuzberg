package kreuzberg

import kreuzberg.dom.ScalaJsNode
import kreuzberg.util.Stateful

import scala.language.implicitConversions

object Assembler {

  def assembleWithId[R, T <: Component.Aux[R]](id: ComponentId, component: T): NodeResult[R, T] = {
    for {
      _         <- Stateful.modify[AssemblyState](_.pushId(id))
      assembled <- component.assemble
      _         <- Stateful.modify[AssemblyState](_.popId)
    } yield ComponentNode.build(id, component, assembled)
  }

  /** Assemble the object as anonymous child. */
  def assembleWithNewId[R, T <: Component.Aux[R]](component: T): NodeResult[R, T] = {
    for {
      id        <- Stateful[AssemblyState, ComponentId](_.generateId)
      assembled <- assembleWithId(id, component)
    } yield {
      assembled
    }
  }

  /** Assembly a named child. */
  def assembleNamedChild[R, T <: Component.Aux[R]](name: String, component: T): NodeResult[R, T] = {
    for {
      id        <- Stateful[AssemblyState, ComponentId](_.ensureChildren(name))
      assembled <- assembleWithId(id, component)
    } yield {
      assembled
    }
  }

  /**
   * Assemble a value as a single component discarding the state. For testcases.
   */
  def single[R, T <: Component.Aux[R]](component: T): Assembly[R] = {
    component.assemble(AssemblyState())._2
  }
}
