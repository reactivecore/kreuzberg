package kreuzberg.engine.common
import kreuzberg.*

type HeadlessOrComponent = Component | HeadlessComponent

/**
 * A Tree Representation of a component.
 * @param component
 *   Kreuzberg Service / Component
 * @param html
 *   HTML representation
 * @param children
 *   children nodes
 * @param handlers
 *   Event bindings
 * @param subscriptions
 *   subscribed models
 */
case class TreeNode(
    component: HeadlessOrComponent,
    html: Html,
    children: Vector[TreeNode],
    handlers: Vector[EventBinding],
    subscriptions: Vector[Identifier]
) {
  override def toString: String = s"Component ${id}/${component}"

  /** Renders the tree node. */
  def render(): String = {
    val sb = StringBuilder()
    renderTo(sb)
    sb.result()
  }

  /** Render into a StringBuilder. */
  def renderTo(sb: StringBuilder): Unit = {
    flatHtml.render(sb, renderChild)
  }

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

  private lazy val childrenMap: Map[Identifier, TreeNode] = children.map { t => t.id -> t }.toMap

  private def renderChild(id: Identifier, sb: StringBuilder): Unit = {
    childrenMap(id).renderTo(sb)
  }

  lazy val flatHtml: FlatHtml = html.flat()

  /** Returns the event identifier. */
  def id: Identifier = component.id
}

object TreeNode {

  object emptyComponent extends Component {
    type Runtime = Unit
    def assemble(using c: AssemblerContext): Assembly = {
      Assembly(emptyRootHtml)
    }
  }

  val empty = TreeNode(
    component = emptyComponent,
    html = emptyRootHtml,
    children = Vector.empty,
    handlers = Vector.empty,
    subscriptions = Vector.empty
  )

  private def emptyRootHtml: Html =
    SimpleHtml("div", children = Vector(SimpleHtmlNode.Text("Empty Root"))).withId(Identifier.RootComponent)
}
