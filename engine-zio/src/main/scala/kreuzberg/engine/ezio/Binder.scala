package kreuzberg.engine.ezio

import kreuzberg.*
import kreuzberg.dom.*
import kreuzberg.engine.common.{Assembler, UpdatePath, BrowserDrawer, ModelValues, TreeNode}
import zio.stream.{ZChannel, ZSink, ZStream}
import zio.*

import scala.collection.mutable
import scala.util.control.NonFatal

object Binder {

  /** Activate a Node on a root element, starting the whole magic. */
  def runOnLoaded(component: Component, rootId: String)(using ServiceRepository): Unit = {
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
  def create(rootElement: ScalaJsElement, main: Component)(using ServiceRepository): Task[Binder] = {
    val browser = new BrowserDrawer(rootElement)
    for {
      modelValues   <- Ref.make(ModelValues())
      previousState <- Ref.make(ModelValues())
      tree          <- Ref.make(TreeNode.empty: TreeNode)
      locator        = new EventManager.Locator {
                         override def unsafeLocate(id: Identifier): ScalaJsElement = browser.findElement(id)
                       }
      eventManager  <- EventManager.create(modelValues, locator)
      mainLock      <- Semaphore.make(1)
    } yield {
      new Binder(browser, modelValues, previousState, tree, rootElement, main, eventManager, mainLock)
    }
  }
}

/** Binds a root element to a Node. */
class Binder(
    browser: BrowserDrawer,
    state: Ref[ModelValues],
    previousState: Ref[ModelValues],
    tree: Ref[TreeNode],
    rootElement: ScalaJsElement,
    main: Component,
    eventManager: EventManager,
    mainLock: Semaphore
)(using serviceRepo: ServiceRepository) {

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
        _            <- Logger.debugZio("Starting redraw")
        currentState <- state.get
        newTree       = Assembler.tree(main)(using buildContext(currentState))
        _            <- previousState.set(currentState)
        _            <- ZIO.attempt(browser.drawRoot(newTree))
        _            <- tree.set(newTree)
        _            <- Logger.debugZio("Activating Events")
        _            <- eventManager.activateEvents(newTree)
        _            <- Logger.debugZio("Activated Events")
        _            <- state.update(state => garbageCollect(newTree, state)._1)
        _            <- Logger.debugZio("End Redraw")
      } yield ()
    }
  }

  private def buildContext(modelValues: ModelValues): AssemblerContext = {
    new AssemblerContext {

      override def value[M](model: Subscribeable[M]): M = modelValues.value(model)

      override def serviceOption[S](using snp: ServiceNameProvider[S]): Option[S] = serviceRepo.serviceOption
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
        _            <- Logger.debugZio(s"Changed Model Ids: ${changedModelIds}/ Components ${changedComponentIds}")
        currentState <- state.get
        currentTree  <- tree.get
        beforeState  <- previousState.get
        path          =
          updateTree(currentTree, changedModelIds, beforeState.toModelValueProvider)(using buildContext(currentState))
        _            <- previousState.set(currentState)
        _            <- tree.set(path.tree)
        _            <- ZIO.attempt(browser.drawUpdatePath(path))
        _            <- ZIO.foreachDiscard(path.changes.flatMap(_.nodes)) {
                          eventManager.activateEvents
                        }
        cleanupResult = garbageCollect(path.tree, currentState)
        _            <- state.set(cleanupResult._1)
        _            <- eventManager.garbageCollect(cleanupResult._2, cleanupResult._3)
      } yield {
        ()
      }
    }
  }

  /** Rebuild tree of changed components. */
  private def updateTree(currentTree: TreeNode, changedModels: Set[Identifier], before: ModelValueProvider)(
      using AssemblerContext
  ): UpdatePath = {
    UpdatePath.build(currentTree, changedModels, before)
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

    Logger.trace(
      s"Garbage Collecting Referenced: ${referencedComponents.size} Components/ ${referencedModels.size} Models"
    )
    Logger.trace(s"  Values:   ${modelValues.modelValues.size} -> ${modelFiltered.modelValues.size}")

    (modelFiltered, referencedComponents, referencedModels)
  }
}
