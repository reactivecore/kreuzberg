package kreuzberg

import kreuzberg.util.{MutableMultimap, Stateful}
import org.scalajs.dom
import org.scalajs.dom.{Element, document, Event as JsEvent}

import scala.collection.mutable
import scala.scalajs.js.timers
import scala.util.control.NonFatal

object Binder {

  /** Activate a Node on a root element, starting the whole magic. */
  def runOnLoaded[T: Assembler](component: T, rootId: String): Unit = {
    document.addEventListener(
      "DOMContentLoaded",
      { (e: dom.Event) =>
        val rootElement = document.getElementById(rootId)
        val binder      = Binder(rootElement, component)
        binder.run()
      }
    )
  }
}

/** Binds a root element to a Node. */
class Binder[T](rootElement: Element, main: T)(implicit assembler: Assembler[T]) extends EventManagerDelegate {
  private var _currentState: AssemblyState = AssemblyState()
  private var _tree: Option[TreeNode]      = None

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
    println("Starting Redraw")
    val (nextState, tree) = assembler.assembleNamedChild("root", main)(_currentState)
    viewer.drawRoot(tree)
    _currentState = nextState
    _tree = Some(tree)
    eventManager.clear()
    eventManager.activateEvents(tree)
    _currentState = garbageCollect(tree, _currentState)
    println("End Redraw")
  }

  private def redrawChanged(changedModels: Set[ModelId]): Unit = {
    val changedContainers = (_currentState.subscribers.collect {
      case (modelId, containerId) if changedModels.contains(modelId) => containerId
    }).toSet

    println(s"Changed Containers: ${changedContainers}")
    if (changedContainers.isEmpty) {
      println("No changed containers, skipping redraw")
      return
    }

    val (updatedState, updatedTree) = updateTree(_tree.get, changedContainers)(_currentState)
    _tree = Some(updatedTree)
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
    val referencedComponents = collectReferencedComponents(tree)

    val componentFiltered = state.copy(
      children = state.children.filterKeys(referencedComponents.contains),
      models = state.models.filterKeys(referencedComponents.contains),
      busses = state.busses.filterKeys(referencedComponents.contains)
    )

    val referencedModels = componentFiltered.models.values.map(_.id).toSet
    val modelFiltered    = componentFiltered.copy(
      modelValues = componentFiltered.modelValues.view.filterKeys(referencedModels.contains).toMap,
      subscribers = componentFiltered.subscribers.filter { case (modelId, componentId) =>
        referencedModels.contains(modelId) && referencedComponents.contains(componentId)
      }.distinct
    )

    println(s"Garbage Collecting Referenced: ${referencedComponents.size} Components/ ${referencedModels.size} Models")
    println(s"  Children: ${state.children.size} -> ${modelFiltered.children.size}")
    println(s"  Models:   ${state.models.size}   -> ${modelFiltered.models.size}")
    println(s"  Busses:   ${state.busses.size}   -> ${modelFiltered.busses.size}")
    println(s"  Values:   ${state.modelValues.size} -> ${modelFiltered.modelValues.size}")
    println(s"  Subscribers: ${state.subscribers.size} -> ${modelFiltered.subscribers.size}")

    modelFiltered
  }

  private def collectReferencedComponents(tree: TreeNode): Set[ComponentId] = {
    val builder                       = Set.newBuilder[ComponentId]
    def iterate(node: TreeNode): Unit = {
      builder += node.id
      node.children.foreach(iterate)
    }
    iterate(tree)
    builder.result()
  }
}
