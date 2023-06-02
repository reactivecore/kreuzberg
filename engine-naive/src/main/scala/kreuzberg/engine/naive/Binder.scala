package kreuzberg.engine.naive

import kreuzberg.*
import kreuzberg.engine.naive.utils.MutableMultimap
import kreuzberg.dom.*
import kreuzberg.engine.common.{Assembler, ModelValues, SimpleServiceRepository, TreeNode}

import scala.collection.mutable
import scala.util.control.NonFatal

object Binder {

  /** Activate a Node on a root element, starting the whole magic. */
  def runOnLoaded(component: Component, rootId: String): Unit = {
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
class Binder(rootElement: ScalaJsElement, main: Component) extends EventManagerDelegate {
  private var _modelValues: ModelValues       = ModelValues()
  private val _serviceRepo: ServiceRepository = new SimpleServiceRepository()
  private var _tree: TreeNode                 = TreeNode.empty

  override def modelValues: ModelValues = _modelValues

  override def onIterationEnd(modelValues: ModelValues, changedModels: Set[Identifier]): Unit = {
    _modelValues = modelValues
    redrawChanged(changedModels)
  }

  override def locate(componentId: Identifier): ScalaJsElement = {
    viewer.findElement(componentId)
  }

  private val viewer       = new Viewer(rootElement)
  private val eventManager = new EventManager(this)

  def run(): Unit = {
    redraw()
  }

  private def redraw(): Unit = {
    Logger.debug("Starting Redraw")
    val tree = Assembler.tree(main)
    viewer.drawRoot(tree)
    _tree = tree
    eventManager.clear()
    eventManager.activateEvents(tree)
    garbageCollect()
    Logger.debug("End Redraw")
  }

  private given assemblerContext: AssemblerContext = new AssemblerContext {
    override def value[M](model: Model[M]): M = _modelValues.readValue(model)

    override def service[S](using provider: Provider[S]): S = _serviceRepo.service
  }

  private def redrawChanged(changedModels: Set[Identifier]): Unit = {
    val changedContainers = _tree.allSubscriptions.collect {
      case (modelId, containerId) if changedModels.contains(modelId) => containerId
    }.toSet

    Logger.debug(s"Changed Containers: ${changedContainers}")
    if (changedContainers.isEmpty) {
      Logger.debug("No changed containers, skipping redraw")
      return
    }

    val updatedTree = _tree.rebuildChanged(changedContainers)
    _tree = updatedTree
    drawChangedEvents(updatedTree, changedContainers)
    activateChangedEvents(updatedTree, changedContainers)
    garbageCollect()
  }

  private def drawChangedEvents(node: TreeNode, changedComponents: Set[Identifier]): Unit = {
    if (changedComponents.contains(node.id)) {
      viewer.updateNode(node)
    } else {
      node.children.foreach(drawChangedEvents(_, changedComponents))
    }
  }

  private def activateChangedEvents(node: TreeNode, changedComponents: Set[Identifier]): Unit = {
    if (changedComponents.contains(node.id)) {
      eventManager.activateEvents(node)
    } else {
      node.children.foreach(activateChangedEvents(_, changedComponents))
    }
  }

  private def garbageCollect(): Unit = {
    val referencedComponents = (_tree.allReferencedComponentIds ++ Iterator(Identifier.RootComponent)).toSet

    val referencedModels = _tree.allSubscriptions.map(_._1).toSet

    val stateUpdated = _modelValues.copy(
      modelValues = _modelValues.modelValues.view.filterKeys(referencedModels.contains).toMap
    )

    eventManager.garbageCollect(referencedComponents, referencedModels)

    Logger.debug(
      s"Garbage Collecting Referenced: ${referencedComponents.size} Components/ ${referencedModels.size} Models"
    )
    Logger.debug(s"  Values:   ${_modelValues.modelValues.size} -> ${stateUpdated.modelValues.size}")

    _modelValues = stateUpdated
  }
}
