package kreuzberg.engine.naive

import kreuzberg.engine.naive.UpdatePath.Change
import kreuzberg.*

/** A list of changes in order to update a Tree. */
private[kreuzberg] case class UpdatePath(
    tree: TreeNode,
    changes: Seq[Change] = Nil
) {
  inline def isEmpty: Boolean = changes.isEmpty
}

/** Figures out incremental changes. */
private[kreuzberg] object UpdatePath {
  sealed trait Change {
    def nodes: Iterable[TreeNode]
  }

  object Change {
    case class Rerender(node: TreeNode)                                          extends Change {
      override def nodes: Iterable[TreeNode] = List(node)
    }
    case class AppendHtml(id: Identifier, node: Vector[TreeNode], html: String)  extends Change {
      override def nodes: Iterable[TreeNode] = node
    }
    case class PrependHtml(id: Identifier, node: Vector[TreeNode], html: String) extends Change {
      override def nodes: Iterable[TreeNode] = node
    }
    case class RebuildHeadless(node: TreeNode)                                   extends Change {
      override def nodes: Iterable[TreeNode] = List(node)
    }
  }

  /** Figures out changes after some models changed. */
  def build(treeNode: TreeNode, changedModels: Set[Identifier], before: ModelValueProvider)(
      using KreuzbergContext
  ): UpdatePath = {

    val builder = new Builder(
      rootNode = treeNode,
      changedModels = changedModels,
      before = before
    )

    builder.build()
  }

  private class Builder(
      rootNode: TreeNode,
      changedModels: Set[Identifier],
      before: ModelValueProvider
  )(using KreuzbergContext) {

    val changedComponents = rootNode.allSubscriptions.collect {
      case (modelId, containerId) if changedModels.contains(modelId) => containerId
    }.toSet

    val changeBuilder = Seq.newBuilder[Change]

    def build(): UpdatePath = {
      if (changedComponents.isEmpty) {
        Logger.trace("No change in containers")
        return UpdatePath(rootNode, Nil)
      }

      val finalTree = collectNodes(rootNode)
      UpdatePath(
        finalTree,
        changeBuilder.result()
      )
    }

    def collectNodes(treeNode: TreeNode): TreeNode = {
      if (changedComponents.contains(treeNode.id)) {
        collectNode(treeNode)
      } else {
        val updated = treeNode.children.map(collectNodes)
        treeNode.copy(
          children = updated
        )
      }
    }

    private def collectNode(treeNode: TreeNode): TreeNode = {
      treeNode.component match {
        case c: Component         =>
          collectComponentNode(treeNode, c)
        case s: HeadlessComponent =>
          collectService(treeNode, s)
      }
    }

    private def collectComponentNode(
        componentNode: TreeNode,
        component: Component
    ): TreeNode = {
      component.update(before) match
        case UpdateResult.Build(assembly)   => {
          rebuildNode(component, assembly)
        }
        case UpdateResult.Prepend(assembly) => {
          prependNode(componentNode, assembly)
        }
        case UpdateResult.Append(assembly)  => {
          appendNode(componentNode, assembly)
        }
    }

    private def collectService(
        treeNode: TreeNode,
        service: HeadlessComponent
    ): TreeNode = {
      val treeNode = Assembler.treeFromHeadless(service)
      changeBuilder += Change.RebuildHeadless(treeNode)
      treeNode
    }

    private def rebuildNode(
        component: Component,
        assembly: Assembly
    ): TreeNode = {
      val treeNode = Assembler.treeFromAssembly(component, assembly)
      changeBuilder += Change.Rerender(treeNode)
      treeNode
    }

    private def prependNode(
        treeNode: TreeNode,
        assembly: Assembly
    ): TreeNode = {
      val newChildren          = assembly.html.embeddedComponents.map { component =>
        Assembler.tree(component)
      }.toVector
      val updatedChildren      = newChildren ++ treeNode.children
      val updatedHtml          = treeNode.html.prependChild(assembly.html)
      val updatedEventHandlers = assembly.handlers ++ treeNode.handlers
      val updatedSubscriptions = assembly.subscriptions.flatMap(_.dependencies) ++ treeNode.subscriptions
      val rendered             = renderSubHtml(assembly.html, newChildren)
      val change               = Change.PrependHtml(treeNode.id, newChildren, rendered)
      changeBuilder += change
      treeNode.copy(
        html = updatedHtml,
        children = updatedChildren,
        subscriptions = updatedSubscriptions,
        handlers = updatedEventHandlers
      )
    }

    def appendNode(
        treeNode: TreeNode,
        assembly: Assembly
    ): TreeNode = {
      val newChildren          = assembly.html.embeddedComponents.map { component =>
        Assembler.tree(component)
      }.toVector
      val updatedChildren      = treeNode.children ++ newChildren
      val updatedHtml          = treeNode.html.appendChild(assembly.html)
      val updatedEventHandlers = treeNode.handlers ++ assembly.handlers
      val updatedSubscriptions = assembly.subscriptions.flatMap(_.dependencies) ++ treeNode.subscriptions
      val rendered             = renderSubHtml(assembly.html, newChildren)
      val change               = Change.AppendHtml(treeNode.id, newChildren, rendered)
      changeBuilder += change
      treeNode.copy(
        html = updatedHtml,
        children = updatedChildren,
        subscriptions = updatedSubscriptions,
        handlers = updatedEventHandlers
      )
    }

    def renderSubHtml(html: Html, nodes: Vector[TreeNode]): String = {
      if (nodes.isEmpty) {
        html.renderToString()
      } else {
        val childrenMap = nodes.map { node => node.id -> node }.toMap

        def renderChild(id: Identifier, sb: StringBuilder): Unit = {
          childrenMap(id).renderTo(sb)
        }

        val sb = StringBuilder()
        html.flat().render(sb, renderChild)

        sb.result()
      }
    }
  }
}
