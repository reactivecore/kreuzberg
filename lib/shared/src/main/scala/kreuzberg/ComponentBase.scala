package kreuzberg

/** Simple base trait for Components. */
trait ComponentBase extends ComponentDsl with Component {

  type Runtime = Unit

  /** Assemble the object. */
  def assemble: AssemblyResult[Unit]

}
