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
      changedModels: Set[Identifier]
  ): Unit

  def locate(componentId: ComponentId): ScalaJsElement
}
