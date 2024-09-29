package kreuzberg

import org.scalajs.dom.Element

/**
 * The base for all UI components.
 *
 * In practice you should use [[ComponentBase]] or [[SimpleComponentBase]]
 */
trait Component extends Identified {

  /** Identifier of the component. */
  final val id = Identifier.next()

  /** Data type of JS Representation. */
  type DomElement <: Element

  /** Assemble the object. */
  def assemble(using context: AssemblerContext): Assembly

  /**
   * Update the component into a new state. By default components are re-rendered.
   *
   * Overriding this method can improve performance, if a component generates large sub-trees and we do not want to
   * rebuild everything (e.g. List-Components).
   */
  def update(before: ModelValueProvider)(using context: AssemblerContext): UpdateResult = {
    UpdateResult.Build(assemble)
  }
}
