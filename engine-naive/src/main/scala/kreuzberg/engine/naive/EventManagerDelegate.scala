package kreuzberg.engine.naive

import kreuzberg.dom.ScalaJsElement
import kreuzberg.*
import kreuzberg.engine.common.ModelValues

/** Callback for EventManager. */
trait EventManagerDelegate {

  /** Returns the current state. */
  def modelValues: ModelValues

  /** Update with a new state */
  def onIterationEnd(
                      state: ModelValues,
                      changedModels: Set[Identifier]
  ): Unit

  def locate(componentId: Identifier): ScalaJsElement
}
