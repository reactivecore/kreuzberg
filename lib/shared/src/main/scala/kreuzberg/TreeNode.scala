package kreuzberg

import kreuzberg.*

private[kreuzberg] type HeadlessOrComponent = Component | HeadlessComponent

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
 *   subscribed models, each paired with the value observed at construction time
 */
private[kreuzberg] case class TreeNode(
    component: HeadlessOrComponent,
    html: Html,
    children: Vector[TreeNode],
    handlers: Vector[EventBinding[?]],
    subscriptions: Vector[SubscriptionRecord]
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
      node    <- iterator
      record  <- node.subscriptions
      modelId <- record.dependencies
    } yield {
      modelId -> node.id
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

  /** True iff every subscribed value still equals the value observed when this node was built. */
  def isUnchanged: Boolean = subscriptions.forall(r => r.subscribable.read() == r.lastValue)

  private lazy val childrenMap: Map[Identifier, TreeNode] = children.map { t => t.id -> t }.toMap

  private def renderChild(id: Identifier, sb: StringBuilder): Unit = {
    childrenMap(id).renderTo(sb)
  }

  lazy val flatHtml: FlatHtml = html.flat()

  /** Returns the event identifier. */
  def id: Identifier = component.id
}

private[kreuzberg] object TreeNode {

  object emptyComponent extends Component {
    type Runtime = Unit
    def assemble: Assembly = {
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

/**
 * A subscription recorded on a [[TreeNode]]. Carries the subscribed [[Subscribeable]] together with the value observed
 * at tree-construction time, so the engine can compare against the current value and skip re-renders whose mapped value
 * has not actually changed.
 */
private[kreuzberg] case class SubscriptionRecord(
    subscribable: Subscribeable[?],
    lastValue: Any
) {
  def dependencies: Seq[Identifier] = subscribable.dependencies
}
