package kreuzberg.engine.naive

import kreuzberg.AssemblyState
import kreuzberg.dom.ScalaJsElement
import kreuzberg.*

/** Callback for EventManager. */
trait EventManagerDelegate {

  /** Returns the current state. */
  def state: AssemblyState

  /** Update with a new state */
  def onIterationEnd(
      state: AssemblyState,
      changedModels: Set[ModelId]
  ): Unit

  def locate(componentId: ComponentId): ScalaJsElement
}
