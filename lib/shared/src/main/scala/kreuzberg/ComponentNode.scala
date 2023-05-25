package kreuzberg

import kreuzberg.util.Stateful

/** Represents an assembled node in a tree. */
sealed trait TreeNode {
  def id: Identifier

  /** Returns children nodes. */
  def children: Vector[TreeNode]

  /** The component. */
  def component: Component

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
  lazy val referencedComponentIds: Set[Identifier] = {
    val builder = Set.newBuilder[Identifier]
    builder += id
    children.foreach { node =>
      builder ++= node.referencedComponentIds
    }
    builder.result()
  }

  /** Find a node by identifier. */
  def find(identifier: Identifier): Option[TreeNode] = {
    if (id == identifier) {
      Some(this)
    } else if (referencedComponentIds.contains(identifier)) {
      val it = children.iterator
      while (it.hasNext) {
        it.next().find(identifier) match {
          case Some(found) => return Some(found)
          case None        => // continue
        }
      }
      None
    } else {
      None
    }
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

object TreeNode {

  object emptyComponent extends Component {
    type Runtime = Unit
    def assemble: AssemblyResult = {
      Stateful.pure(Assembly(emptyRootHtml))
    }
  }

  val empty = ComponentNode[emptyComponent.type](
    component = emptyComponent,
    html = emptyRootHtml.flat(),
    children = Vector.empty,
    handlers = Vector.empty
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
    handlers: Vector[EventBinding]
) extends TreeNodeC[T] {
  override def toString: String = s"Component ${id}/${component}"

  private lazy val childrenMap: Map[Identifier, TreeNode] = children.map { t => t.id -> t }.toMap

  private def renderChild(id: Identifier, sb: StringBuilder): Unit = {
    childrenMap(id).renderTo(sb)
  }

  override def renderTo(sb: StringBuilder): Unit = {
    html.render(sb, renderChild)
  }

  override def id: Identifier = component.id
}
