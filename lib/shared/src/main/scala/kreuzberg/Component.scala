package kreuzberg

import kreuzberg.dom.ScalaJsElement

/** The base for all components. */
trait Component {

  /** Identifier of the component. */
  final val id = Identifier.next()

  type DomElement <: ScalaJsElement

  /** Assemble the object. */
  def assemble: AssemblyResult

  /** Comment, which will be built into the node. Can be disabled by returning "" */
  def comment: String = getClass.getSimpleName.stripSuffix("$")
}
