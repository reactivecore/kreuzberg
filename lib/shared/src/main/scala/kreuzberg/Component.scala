package kreuzberg

/** The base for all components. */
trait Component {

  /** Identifier of the component. */
  final val id = Identifier.next()

  /** The runtime type. */
  type Runtime

  /** Assemble the object. */
  def assemble: AssemblyResult[Runtime]

  /** Access the runtime state. */
  def state(using rc: RuntimeContext): Runtime = rc.runtimeState(this)

  /** Comment, which will be built into the node. Can be disabled by returning "" */
  def comment: String = getClass.getSimpleName.stripSuffix("$")
}

object Component {
  type Aux[R] = Component {
    type Runtime = R
  }
}
