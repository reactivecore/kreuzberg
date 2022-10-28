package kreuzberg

import org.scalajs.dom.Element as JsElement

/** Simple base trait for Components. */
trait ComponentBase extends ComponentDsl {

  /** Assemble the object. */
  def assemble: AssemblyResult

}

object ComponentBase {

  implicit def assembler[T <: ComponentBase]: Assembler[T] = { value =>
    value.assemble
  }
}
