package kreuzberg

import kreuzberg.util.Stateful

/** Represents an assembled node in a tree. */
sealed trait TreeNode {
  def id: ComponentId

  /** Returns children nodes. */
  def children: Vector[TreeNode]

  /** Event Handlers. */
  def handlers: Vector[EventBinding]

  /** Renders the tree node. */
  def render(): String = {
    val sb = StringBuilder()
    renderTo(sb)
    sb.result()
  }

  def renderTo(sb: StringBuilder): Unit

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

/** TreeNode with Component Type. */
sealed trait TreeNodeC[T <: Component] extends TreeNode {
  def component: T
}

/** TreeNode with representation type. */
sealed trait TreeNodeR[+R] extends TreeNode {
  def runtimeProvider: RuntimeProvider[R]
}

object TreeNode {

  object emptyComponent extends Component {
    type Runtime = Unit
    def assemble: AssemblyResult[Unit] = {
      Stateful.pure(Assembly(emptyRootHtml))
    }
  }

  val empty = ComponentNode.build[Unit, emptyComponent.type](
    AssemblyState.RootComponent,
    emptyComponent,
    Assembly(emptyRootHtml)
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
case class ComponentNode[+R, T <: Component.Aux[R]](
    id: ComponentId,
    component: T,
    html: FlatHtml,
    children: Vector[TreeNode],
    runtimeProvider: RuntimeProvider[R],
    handlers: Vector[EventBinding]
) extends TreeNodeC[T]
    with TreeNodeR[R] {
  override def toString: String = s"Component ${id}/${component}"

  private lazy val childrenMap: Map[ComponentId, TreeNode] = children.map { t => t.id -> t }.toMap

  private def renderChild(id: ComponentId, sb: StringBuilder): Unit = {
    childrenMap(id).renderTo(sb)
  }

  override def renderTo(sb: StringBuilder): Unit = {
    html.render(sb, renderChild)
  }
}

object ComponentNode {
  def build[R, T <: Component.Aux[R]](
      id: ComponentId,
      component: T,
      assembly: Assembly[R]
  ): ComponentNode[R, T] = {
    val withId    = assembly.html.withId(id)
    val comment   = component.comment
    val htmlToUse = if (comment.isEmpty()) {
      withId
    } else {
      withId.addComment(comment)
    }
    ComponentNode(
      id,
      component,
      htmlToUse.flat(),
      assembly.nodes,
      assembly.provider,
      assembly.handlers
    )
  }
}
