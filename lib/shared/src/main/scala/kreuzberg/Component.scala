package kreuzberg

import kreuzberg.dom.ScalaJsElement

/**
 * The base for all components.
 *
 * In practice you should use [[ComponentBase]] or [[SimpleComponentBase]]
 */
trait Component {

  /** Identifier of the component. */
  final val id = Identifier.next()

  /** Data type of JS Representation. */
  type DomElement <: ScalaJsElement

  /** Assemble the object. */
  def assemble(using context: AssemblerContext): Assembly

  /** Comment, which will be built into the node. Can be disabled by returning "" */
  def comment: String = getClass.getSimpleName.stripSuffix("$")
}
