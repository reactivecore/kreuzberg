package kreuzberg

import org.scalajs.dom.Element

/** Encapsulates a runtime state field. */
trait RuntimeState[S] {

  def map[S2](f: S => S2): RuntimeState[S2] = {
    RuntimeState.Mapping(this, f)
  }

  /** Read the state from Handler */
  def read()(using c: Changer): S
}

/** A State which can also be set. */
trait RuntimeProperty[S] extends RuntimeState[S] {

  /** Sets the value. */
  def set(value: S)(using c: Changer): Unit

  /** Maps and Contra Maps the value. */
  def xmap[U](mapFn: S => U, contraMapFn: U => S): RuntimeProperty[U] =
    RuntimeState.CrossMapping(this, mapFn, contraMapFn)
}

object RuntimeState {

  /** Base for runtime state. */
  trait JsRuntimeStateBase[D <: Element, S] extends RuntimeState[S] {
    def componentId: Identifier
    def getter: D => S

    protected def getElement()(using c: Changer): D = {
      c.locate(componentId).asInstanceOf[D]
    }

    override def read()(using c: Changer): S = {
      getter(getElement())
    }
  }

  /**
   * Encapsulates a JS DOM runtime state field.
   *
   * @param componentId
   *   component ID
   * @param getter
   *   function which fetches the state from DOM element type
   * @tparam D
   *   DOM Element Type
   * @tparam S
   *   Return type
   */
  case class JsRuntimeState[D <: Element, S](
      componentId: Identifier,
      getter: D => S
  ) extends JsRuntimeStateBase[D, S]

  /** Encapsulates a read/writable property. */
  case class JsProperty[D <: Element, S](
      componentId: Identifier,
      getter: D => S,
      setter: (D, S) => Unit
  ) extends JsRuntimeStateBase[D, S]
      with RuntimeProperty[S] {

    override def set(value: S)(using Changer): Unit = {
      setter(getElement(), value)
    }
  }

  case class Mapping[S1, S2](
      from: RuntimeState[S1],
      mapFn: S1 => S2
  ) extends RuntimeState[S2] {
    override def read()(using Changer): S2 = mapFn(from.read())
  }

  case class CrossMapping[S1, S2](
      from: RuntimeProperty[S1],
      mapFn: S1 => S2,
      contraMap: S2 => S1
  ) extends RuntimeProperty[S2] {
    override def read()(using Changer): S2 = {
      mapFn(from.read())
    }

    override def set(value: S2)(using Changer): Unit = {
      from.set(contraMap(value))
    }
  }

  /** A constant pseudo state. */
  case class Const[S](value: S) extends RuntimeState[S] {
    override def read()(using Changer): S = value
  }
}
