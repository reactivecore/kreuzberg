package kreuzberg

/** Represents an assembled node in a tree. */
sealed trait TreeNode {
  def id: ComponentId
  def assembly: Assembly

  /** Returns children nodes. */
  def children: Vector[TreeNode] = assembly.nodes

  /** Renders the tree node. */
  def render(): Html = {
    assembly.renderWithId(id)
  }

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

  val empty = ComponentNode[Unit](
    AssemblyState.RootComponent,
    (),
    emptyRootHtml,
    Assembler.plain(_ => emptyRootHtml)
  )

  private def emptyRootHtml: Html =
    SimpleHtml("div", children = Vector(SimpleHtmlNode.Text("Empty Root"))).withId(AssemblyState.RootComponent)
}

/** A Representation of the component. */
case class ComponentNode[T](
    id: ComponentId,
    value: T,
    assembly: Assembly,
    assembler: Assembler[T]
) extends TreeNode {
  override def toString: String = s"Component ${id}/${value}"
}
