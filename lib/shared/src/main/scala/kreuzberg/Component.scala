package kreuzberg

import org.scalajs.dom.Element

import scala.annotation.unused
import scala.concurrent.ExecutionContext

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
  def assemble: Assembly

  protected implicit def ec: ExecutionContext = KreuzbergContext.get().ec

  /** Schedule some code. */
  protected def schedule[T](f: () => Unit): Unit = {
    KreuzbergContext.get().changer.call(f)
  }

  /**
   * Update the component into a new state. By default components are re-rendered.
   *
   * Overriding this method can improve performance, if a component generates large sub-trees and we do not want to
   * rebuild everything (e.g. List-Components).
   */
  def update(@unused before: ModelValueProvider): UpdateResult = {
    UpdateResult.Build(assemble)
  }
}
