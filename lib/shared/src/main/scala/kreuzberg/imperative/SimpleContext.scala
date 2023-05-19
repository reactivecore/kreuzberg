package kreuzberg.imperative

import kreuzberg.util.Stateful
import kreuzberg.{AssemblyState, EventBinding}

/** A simple imperative variant of AssemblyState which also collects event bindings. */
class SimpleContext(private var _state: AssemblyState) {
  private val _eventBindings = Vector.newBuilder[EventBinding]

  def transform[T](f: Stateful[AssemblyState, T]): T = {
    val (nextState, value) = f(_state)
    _state = nextState
    value
  }

  def transformFn[T](f: AssemblyState => (AssemblyState, T)): T = {
    val (nextState, value) = f(_state)
    _state = nextState
    value
  }

  def get[T](f: AssemblyState => T): T = {
    f(_state)
  }

  def state: AssemblyState = _state

  def addEventBinding(binding: EventBinding): Unit = {
    _eventBindings += binding
  }

  def eventBindings(): Vector[EventBinding] = _eventBindings.result()
}
