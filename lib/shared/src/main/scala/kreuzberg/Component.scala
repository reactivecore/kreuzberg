package kreuzberg

/** The base for all components. */
trait Component {

  /** The runtime type. */
  type Runtime

  /** Assemble the object. */
  def assemble: AssemblyResult[Runtime]

  /** Comment, which will be built into the node. Can be disabled by returning "" */
  def comment: String = getClass.getSimpleName.stripSuffix("$")
}

object Component {
  type Aux[R] = Component {
    type Runtime = R
  }
}
