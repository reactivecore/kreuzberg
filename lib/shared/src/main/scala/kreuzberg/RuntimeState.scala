package kreuzberg

import kreuzberg.dom.ScalaJsElement

/** Encapsulates a runtime state field. */
sealed trait RuntimeState[S] {

  def zip[S2](other: RuntimeState[S2]): RuntimeState[(S, S2)] = {
    RuntimeState.And(this, other)
  }

  def map[S2](f: S => S2): RuntimeState[S2] = {
    RuntimeState.Mapping(this, f)
  }
}

object RuntimeState {

  /**
   * Encapsulates a JS DOM runtime state field.
   *
   * @param componentId
   *   component ID
   * @param fetcher
   *   function which fetches the state from DOM element type
   * @tparam D
   *   DOM Element Type
   * @tparam S
   *   Return type
   */
  case class JsRuntimeState[D <: ScalaJsElement, S](
      componentId: Identifier,
      fetcher: D => S
  ) extends RuntimeState[S]

  case class And[S1, S2](
      left: RuntimeState[S1],
      right: RuntimeState[S2]
  ) extends RuntimeState[(S1, S2)]

  case class Mapping[S1, S2](
      from: RuntimeState[S1],
      mapFn: S1 => S2
  ) extends RuntimeState[S2]
}
