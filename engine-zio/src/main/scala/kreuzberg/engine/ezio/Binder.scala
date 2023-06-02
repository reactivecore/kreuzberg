package kreuzberg.engine.ezio

import kreuzberg.*
import kreuzberg.dom.*
import kreuzberg.engine.common.{Assembler, ModelValues, SimpleServiceRepository, TreeNode}
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
      modelValues   <- Ref.make(ModelValues())
      tree          <- Ref.make(TreeNode.empty: TreeNode)
      locator        = new EventManager.Locator {
                         override def unsafeLocate(id: Identifier): ScalaJsElement = viewer.findElementUnsafe(id)
                       }
      eventManager2 <- EventManager.create(modelValues, locator)
      mainLock      <- Semaphore.make(1)
    } yield {
      new Binder(modelValues, tree, rootElement, main, eventManager2, mainLock)
    }
  }
}

/** Binds a root element to a Node. */
class Binder(
    state: Ref[ModelValues],
    tree: Ref[TreeNode],
    rootElement: ScalaJsElement,
    main: Component,
    eventManager: EventManager,
    mainLock: Semaphore
) {

  private val viewer                         = new Viewer(rootElement)
  private val serviceRepo: ServiceRepository = new SimpleServiceRepository

  def run(): Task[Unit] = {
    for {
      _ <- eventManager.iterationStream.runForeach(onChangedModels).forkZioLogged("Iteration Stream")
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
        context <- buildContext()
        newTree  = Assembler.tree(main)(using context)
        _       <- viewer.drawRoot(newTree)
        _       <- tree.set(newTree)
        _       <- Logger.debugZio("Activating Events")
        _       <- eventManager.activateEvents(newTree)
        _       <- Logger.debugZio("Activated Events")
        _       <- state.update(state => garbageCollect(newTree, state)._1)
        _       <- Logger.debugZio("End Redraw")
      } yield ()
    }
  }

  private def buildContext(): Task[AssemblerContext] = {
    state.get.map { state =>
      new AssemblerContext:
        override def value[M](model: Model[M]): M = state.readValue(model)

        override def service[S](using provider: Provider[S]): S = serviceRepo.service
    }
  }

  private def onChangedModels(modelIds: Chunk[Identifier]): Task[Unit] = {
    val modelIdSet = modelIds.toSet
    tree.get.flatMap { tree =>
      val componentIds = (tree.allSubscriptions.collect {
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
        context    <- buildContext()
        _          <- updateTree(changedComponentIds)(using context)
        treeNow    <- tree.get
        _          <- drawChangedEvents(treeNow, changedComponentIds)
        _          <- activateChangedEvents(treeNow, changedComponentIds)
        referenced <- state.modify { state =>
                        val (updatedState, components, models) = garbageCollect(treeNow, state)
                        (components, models) -> updatedState
                      }
        _          <- eventManager.garbageCollect(referenced._1, referenced._2)
      } yield {
        ()
      }
    }
  }

  /** Rebuild tree of changed components. */
  private def updateTree(changed: Set[Identifier])(using AssemblerContext): Task[Unit] = {
    tree.update(_.rebuildChanged(changed))
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
      eventManager.activateEvents(node)
    } else {
      ZIO.foreachDiscard(node.children)(activateChangedEvents(_, changed))
    }
  }

  /** Garbage collects the models, returns referenced New state, and referenced Components and Models */
  private def garbageCollect(
      tree: TreeNode,
      modelValues: ModelValues
  ): (ModelValues, Set[Identifier], Set[Identifier]) = {
    val referencedComponents = (tree.allReferencedComponentIds ++ Iterator(Identifier.RootComponent)).toSet

    val referencedModels = tree.allSubscriptions.map(_._1).toSet
    val modelFiltered    = modelValues.copy(
      modelValues = modelValues.modelValues.view.filterKeys(referencedModels.contains).toMap
    )

    Logger.debug(
      s"Garbage Collecting Referenced: ${referencedComponents.size} Components/ ${referencedModels.size} Models"
    )
    Logger.debug(s"  Values:   ${modelValues.modelValues.size} -> ${modelFiltered.modelValues.size}")

    (modelFiltered, referencedComponents, referencedModels)
  }
}
