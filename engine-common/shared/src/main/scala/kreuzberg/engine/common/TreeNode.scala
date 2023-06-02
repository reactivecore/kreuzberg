package kreuzberg.engine.common
import kreuzberg.*

/** Represents an assembled node in a tree. */
sealed trait TreeNode {
  def id: Identifier

  /** Returns children nodes. */
  def children: Vector[TreeNode]

  /** The component. */
  def component: Component

  /** Event Handlers. */
  def handlers: Vector[EventBinding]

  /** Model Ids a node subscribed to. */
  def subscriptions: Vector[Identifier]

  /** Renders the tree node. */
  def render(): String = {
    val sb = StringBuilder()
    renderTo(sb)
    sb.result()
  }

  def renderTo(sb: StringBuilder): Unit

  /** All subscriptions of this tree, modelId to component id. */
  def allSubscriptions: Iterator[(Identifier, Identifier)] = {
    for {
      node         <- iterator
      subscription <- node.subscriptions
    } yield {
      subscription -> node.id
    }
  }

  /** All referenced component ids. */
  def allReferencedComponentIds: Iterator[Identifier] = {
    iterator.map(_.id)
  }

  def foreach(f: TreeNode => Unit): Unit = {
    children.foreach(_.foreach(f))
    f(this)
  }

  def iterator: Iterator[TreeNode] = {
    Iterator(this) ++ children.iterator.flatMap(_.iterator)
  }

  /** Rebuild changed nodes. */
  def rebuildChanged(changed: Set[Identifier])(using assemblerContext: AssemblerContext): TreeNode
}

object TreeNode {

  object emptyComponent extends Component {
    type Runtime = Unit
    def assemble(using c: AssemblerContext): Assembly = {
      Assembly(emptyRootHtml)
    }
  }

  val empty = ComponentNode[emptyComponent.type](
    component = emptyComponent,
    html = emptyRootHtml.flat(),
    children = Vector.empty,
    handlers = Vector.empty,
    subscriptions = Vector.empty
  )

  private def emptyRootHtml: Html =
    SimpleHtml("div", children = Vector(SimpleHtmlNode.Text("Empty Root"))).withId(Identifier.RootComponent)
}

/**
 * A Tree Representation of a component.
 * @tparam T
 *   value of the component
 */
case class ComponentNode[T <: Component](
    component: T,
    html: FlatHtml,
    children: Vector[TreeNode],
    handlers: Vector[EventBinding],
    subscriptions: Vector[Identifier]
) extends TreeNode {
  override def toString: String = s"Component ${id}/${component}"

  private lazy val childrenMap: Map[Identifier, TreeNode] = children.map { t => t.id -> t }.toMap

  private def renderChild(id: Identifier, sb: StringBuilder): Unit = {
    childrenMap(id).renderTo(sb)
  }

  override def renderTo(sb: StringBuilder): Unit = {
    html.render(sb, renderChild)
  }

  override def id: Identifier = component.id

  override def rebuildChanged(changed: Set[Identifier])(using assemblerContext: AssemblerContext): TreeNode = {
    if (changed.contains(id)) {
      Assembler.tree(component)
    } else {
      if (children.isEmpty) {
        this
      } else {
        val updated = children.map(_.rebuildChanged(changed))
        copy(
          children = updated
        )
      }
    }
  }
}
