package kreuzberg

/** Simple base trait for Components. */
trait ComponentBase extends ComponentDsl {

  /** Assemble the object. */
  def assemble: AssemblyResult[Unit]

}

object ComponentBase {

  implicit def assembler[T <: ComponentBase]: Assembler.Aux[T, Unit] = { value =>
    value.assemble
  }
}
