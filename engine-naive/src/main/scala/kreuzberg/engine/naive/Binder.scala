package kreuzberg.engine.naive

import kreuzberg.*
import kreuzberg.util.Stateful
import kreuzberg.engine.naive.utils.MutableMultimap
import kreuzberg.util.Stateful
import kreuzberg.dom.*
import scala.collection.mutable
import scala.util.control.NonFatal

object Binder {

  /** Activate a Node on a root element, starting the whole magic. */
  def runOnLoaded[T: Assembler](component: T, rootId: String): Unit = {
    org.scalajs.dom.document.addEventListener(
      "DOMContentLoaded",
      { (e: ScalaJsEvent) =>
        Logger.info("Initializing naive Kreuzberg engine")
        val rootElement = org.scalajs.dom.document.getElementById(rootId)
        val binder      = Binder(rootElement, component)
        binder.run()
      }
    )
  }
}

/** Binds a root element to a Node. */
class Binder[T](rootElement: ScalaJsElement, main: T)(implicit assembler: Assembler[T]) extends EventManagerDelegate {
  private var _currentState: AssemblyState = AssemblyState()
  private var _tree: TreeNode      = TreeNode.empty

  override def state: AssemblyState = _currentState

  override def onIterationEnd(state: AssemblyState, changedModels: Set[ModelId]): Unit = {
    _currentState = state
    redrawChanged(changedModels)
  }

  override def locate(componentId: ComponentId): ScalaJsElement = {
    viewer.findElement(componentId)
  }

  private val viewer       = new Viewer(rootElement)
  private val eventManager = new EventManager(this)

  def run(): Unit = {
    redraw()
  }

  private def redraw(): Unit = {
    Logger.debug("Starting Redraw")
    val (nextState, tree) = assembler.assembleNamedChild("root", main)(_currentState)
    viewer.drawRoot(tree)
    _currentState = nextState
    _tree = tree
    eventManager.clear()
    eventManager.activateEvents(tree)
    _currentState = garbageCollect(tree, _currentState)
    Logger.debug("End Redraw")
  }

  private def redrawChanged(changedModels: Set[ModelId]): Unit = {
    val changedContainers = (_currentState.subscribers.collect {
      case (modelId, containerId) if changedModels.contains(modelId) => containerId
    }).toSet

    Logger.debug(s"Changed Containers: ${changedContainers}")
    if (changedContainers.isEmpty) {
      Logger.debug("No changed containers, skipping redraw")
      return
    }

    val (updatedState, updatedTree) = updateTree(_tree, changedContainers)(_currentState)
    _tree = updatedTree
    drawChangedEvents(updatedTree, changedContainers)
    activateChangedEvents(updatedTree, changedContainers)
    _currentState = garbageCollect(updatedTree, updatedState)
  }

  private def updateTree(node: TreeNode, changed: Set[ComponentId]): Stateful[AssemblyState, TreeNode] = {
    if (changed.contains(node.id)) {
      reassembleNode(node)
    } else {
      transformNodeChildren(node, updateTree(_, changed))
    }
  }

  private def reassembleNode(node: TreeNode): Stateful[AssemblyState, TreeNode] = {
    node match {
      case r: ComponentNode[_] =>
        r.assembler.assembleWithId(node.id, r.value)
    }
  }

  private def transformNodeChildren(
      node: TreeNode,
      f: TreeNode => Stateful[AssemblyState, TreeNode]
  ): Stateful[AssemblyState, TreeNode] = {
    node match {
      case r: ComponentNode[_] =>
        r.assembly match {
          case p: Assembly.Pure      => Stateful.pure(node)
          case c: Assembly.Container =>
            for {
              updated <- Stateful.accumulate(c.nodes)(f)
            } yield {
              val updatedAssembly = c.copy(
                nodes = updated
              )
              r.copy(assembly = updatedAssembly)
            }
        }
    }
  }

  private def drawChangedEvents(node: TreeNode, changed: Set[ComponentId]): Unit = {
    if (changed.contains(node.id)) {
      viewer.updateNode(node)
    } else {
      node.children.foreach(drawChangedEvents(_, changed))
    }
  }

  private def activateChangedEvents(node: TreeNode, changed: Set[ComponentId]): Unit = {
    if (changed.contains(node.id)) {
      eventManager.activateEvents(node)
    } else {
      node.children.foreach(activateChangedEvents(_, changed))
    }
  }

  private def garbageCollect(tree: TreeNode, state: AssemblyState): AssemblyState = {
    val referencedComponents = tree.referencedComponentIds()

    val componentFiltered = state.copy(
      children = state.children.filterKeys(referencedComponents.contains),
      models = state.models.filterKeys(referencedComponents.contains),
      services = state.services.filterKeys(referencedComponents.contains)
    )

    val referencedModels = componentFiltered.models.values.map(_.id).toSet
    val modelFiltered    = componentFiltered.copy(
      modelValues = componentFiltered.modelValues.view.filterKeys(referencedModels.contains).toMap,
      subscribers = componentFiltered.subscribers.filter { case (modelId, componentId) =>
        referencedModels.contains(modelId) && referencedComponents.contains(componentId)
      }.distinct
    )

    eventManager.garbageCollect(referencedComponents, referencedModels)

    Logger.debug(
      s"Garbage Collecting Referenced: ${referencedComponents.size} Components/ ${referencedModels.size} Models"
    )
    Logger.debug(s"  Children: ${state.children.size} -> ${modelFiltered.children.size}")
    Logger.debug(s"  Models:   ${state.models.size}   -> ${modelFiltered.models.size}")
    Logger.debug(s"  Values:   ${state.modelValues.size} -> ${modelFiltered.modelValues.size}")
    Logger.debug(s"  Subscribers: ${state.subscribers.size} -> ${modelFiltered.subscribers.size}")
    // println(s"  Referenced Models: ${referencedModels.toSeq.sortBy(_.id)}")
    // println(s"  Referenced Components: ${referencedComponents.toSeq.sortBy(_.id)}")

    modelFiltered
  }
}
