package kreuzberg.engine.ezio

import kreuzberg.*
import kreuzberg.util.Stateful
import kreuzberg.dom.*
import zio.stream.{ZChannel, ZSink, ZStream}
import zio.*

import scala.collection.mutable
import scala.util.control.NonFatal

object Binder {

  /** Activate a Node on a root element, starting the whole magic. */
  def runOnLoaded(component: Component, rootId: String): Unit = {
    org.scalajs.dom.document.addEventListener(
      "DOMContentLoaded",
      { (e: ScalaJsEvent) =>
        val rootElement = org.scalajs.dom.document.getElementById(rootId)

        val runtime = zio.Runtime.default
        zio.Unsafe.unsafe { implicit unsafe =>
          runtime.unsafe
            .runToFuture(
              for {
                _      <- Logger.infoZio("Initializing ZIO based kreuzberg engine")
                binder <- create(rootElement, component)
                result <- binder
                            .run()
                            .tapError { error =>
                              Logger.warnZio(s"Binder failed with error: ${error.getMessage}")
                            }
                            .tap { _ =>
                              Logger.infoZio("Binder exited")
                            }
              } yield {
                result
              }
            )
        }
      }
    )
  }

  /** Create a Binder. */
  def create(rootElement: ScalaJsElement, main: Component): Task[Binder] = {
    val viewer = new Viewer(rootElement)
    for {
      state         <- Ref.make(AssemblyState())
      tree          <- Ref.make(TreeNode.empty: TreeNode)
      locator        = new EventManager.Locator {
                         override def unsafeLocate(id: Identifier): ScalaJsElement = viewer.findElementUnsafe(id)
                       }
      eventManager2 <- EventManager.create(state, locator)
      mainLock      <- Semaphore.make(1)
    } yield {
      new Binder(state, tree, rootElement, main, eventManager2, mainLock)
    }
  }
}

/** Binds a root element to a Node. */
class Binder(
    state: Ref[AssemblyState],
    tree: Ref[TreeNode],
    rootElement: ScalaJsElement,
    main: Component,
    eventManager2: EventManager,
    mainLock: Semaphore
) {

  private val viewer = new Viewer(rootElement)

  def run(): Task[Unit] = {
    for {
      _ <- eventManager2.iterationStream.runForeach(onChangedModels).forkZioLogged("Iteration Stream")
      _ <- firstDraw()
      _ <- ZIO.never
    } yield {
      ()
    }
  }

  private def firstDraw(): Task[Unit] = {
    mainLock.withPermit {
      for {
        _       <- Logger.debugZio("Starting redraw")
        newTree <- Assembler.tree(main).onRef(state)
        _       <- viewer.drawRoot(newTree)
        _       <- tree.set(newTree)
        _       <- Logger.debugZio("Activating Events")
        _       <- eventManager2.activateEvents(newTree)
        _       <- Logger.debugZio("Activated Events")
        _       <- state.update(state => garbageCollect(newTree, state)._1)
        _       <- Logger.debugZio("End Redraw")
      } yield ()
    }
  }

  private def onChangedModels(modelIds: Chunk[Identifier]): Task[Unit] = {
    val modelIdSet = modelIds.toSet
    state.get.flatMap { currentState =>
      val componentIds = (currentState.subscribers.view.collect {
        case (modelId, componentIds) if modelIdSet.contains(modelId) => componentIds
      }).toSet
      onChangedModelsContainers(modelIdSet, componentIds)
    }
  }

  private def onChangedModelsContainers(
      changedModelIds: Set[Identifier],
      changedComponentIds: Set[Identifier]
  ): Task[Unit] = {
    if (changedComponentIds.isEmpty) {
      return ZIO.unit
    }
    mainLock.withPermit {
      for {
        _          <- Logger.debugZio(s"Changed Model Ids: ${changedModelIds}/ Components ${changedComponentIds}")
        _          <- updateTree(changedComponentIds)
        treeNow    <- tree.get
        _          <- drawChangedEvents(treeNow, changedComponentIds)
        _          <- activateChangedEvents(treeNow, changedComponentIds)
        referenced <- state.modify { state =>
                        val (updatedState, components, models) = garbageCollect(treeNow, state)
                        (components, models) -> updatedState
                      }
        _          <- eventManager2.garbageCollect(referenced._1, referenced._2)
      } yield {
        ()
      }
    }
  }

  /** Rebuild tree of changed components. */
  private def updateTree(changed: Set[Identifier]): Task[Unit] = {
    for {
      tree0   <- tree.get
      updated <- updateSubtree(tree0, changed)
      _       <- tree.set(updated)
    } yield {
      ()
    }
  }

  /** Rebuild a subtree. */
  private def updateSubtree(node: TreeNode, changed: Set[Identifier]): Task[TreeNode] = {
    if (changed.contains(node.id)) {
      reassembleNode(node)
    } else {
      transformNodeChildren(node, updateSubtree(_, changed))
    }
  }

  /** Reassemble a single node. */
  private def reassembleNode(node: TreeNode): Task[TreeNode] = {
    node match {
      case r: ComponentNode[_] =>
        Assembler.tree(r.component).onRef(state)
    }
  }

  /** Transform node children using f */
  private def transformNodeChildren(node: TreeNode, f: TreeNode => Task[TreeNode]): Task[TreeNode] = {
    node match {
      case c: ComponentNode[_] =>
        if (c.children.isEmpty) {
          ZIO.succeed(node)
        } else {
          ZIO.foreach(c.children)(f).map { x =>
            c.copy(
              children = x
            )
          }
        }
    }
  }

  private def drawChangedEvents(node: TreeNode, changed: Set[Identifier]): Task[Unit] = {
    if (changed.contains(node.id)) {
      viewer.updateNode(node)
    } else {
      ZIO.foreachDiscard(node.children)(drawChangedEvents(_, changed))
    }
  }

  private def activateChangedEvents(node: TreeNode, changed: Set[Identifier]): Task[Unit] = {
    if (changed.contains(node.id)) {
      eventManager2.activateEvents(node)
    } else {
      ZIO.foreachDiscard(node.children)(activateChangedEvents(_, changed))
    }
  }

  /** Garbage collects the AssemblyState, returns referenced New state, and referenced Components and Models */
  private def garbageCollect(
      tree: TreeNode,
      state: AssemblyState
  ): (AssemblyState, Set[Identifier], Set[Identifier]) = {
    val referencedComponents = tree.referencedComponentIds + Identifier.RootComponent

    val componentFiltered = state.copy(
      services = state.services.filterKeys(referencedComponents.contains)
    )

    val referencedModels = componentFiltered.subscribers.map(_._1).toSet
    val modelFiltered    = componentFiltered.copy(
      modelValues = componentFiltered.modelValues.view.filterKeys(referencedModels.contains).toMap,
      subscribers = componentFiltered.subscribers.filter { case (modelId, componentId) =>
        referencedModels.contains(modelId) && referencedComponents.contains(componentId)
      }.distinct
    )

    Logger.debug(
      s"Garbage Collecting Referenced: ${referencedComponents.size} Components/ ${referencedModels.size} Models"
    )
    Logger.debug(s"  Values:   ${state.modelValues.size} -> ${modelFiltered.modelValues.size}")
    Logger.debug(s"  Subscribers: ${state.subscribers.size} -> ${modelFiltered.subscribers.size}")

    (modelFiltered, referencedComponents, referencedModels)
  }
}
