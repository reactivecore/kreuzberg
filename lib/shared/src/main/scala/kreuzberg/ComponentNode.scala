package kreuzberg

/** Represents an assembled node in a tree. */
sealed trait TreeNode {
  def id: ComponentId
  def assembly: Assembly[Any]

  /** Returns children nodes. */
  def children: Vector[TreeNode] = assembly.nodes

  /** Renders the tree node. */
  def render(): Html

  /** Returns referenced component ids. */
  def referencedComponentIds(): Set[ComponentId] = {
    val builder = Set.newBuilder[ComponentId]
    builder += AssemblyState.RootComponent // Root Component is always referenced.
    foreach { node => builder += node.id }
    builder.result()
  }

  def foreach(f: TreeNode => Unit): Unit = {
    children.foreach(_.foreach(f))
    f(this)
  }
}

object TreeNode {

  val empty = ComponentNode[Unit, Unit](
    AssemblyState.RootComponent,
    (),
    emptyRootHtml,
    Assembler.plain(_ => emptyRootHtml)
  )

  private def emptyRootHtml: Html =
    SimpleHtml("div", children = Vector(SimpleHtmlNode.Text("Empty Root"))).withId(AssemblyState.RootComponent)
}

/**
 * A Representation of the component.
 * @tparam T
 *   value of the component
 * @tparam R
 *   runtime type
 */
case class ComponentNode[T, +R](
    id: ComponentId,
    value: T,
    assembly: Assembly[R],
    assembler: Assembler[T]
) extends TreeNode {
  override def toString: String = s"Component ${id}/${value}"

  override def render(): Html = {
    val valueTypeName = value.getClass.getSimpleName.stripSuffix("$") // TODO: Configurable?!
    assembly.renderWithId(id).addComment(valueTypeName)
  }
}
