package kreuzberg.engine.ezio

import kreuzberg.*
import kreuzberg.dom.*
import kreuzberg.engine.ezio.EventManager.{ComponentSubscriber, Iteration, Locator, ModelSubscriber, create}
import kreuzberg.engine.ezio.utils.MultiListMap
import zio.stream.{ZSink, ZStream}
import zio.*

import scala.concurrent.Future

type XStream[T] = ZStream[Any, Nothing, T]

/** Responsible for activating Events on components and putting them all into one single Iteration Stream. */
class EventManager(
    locator: Locator,
    hub: Hub[ModelId],
    state: Ref[AssemblyState],
    eventRegistry: JsEventRegistry[ComponentId],
    modelSubscribers: Ref[MultiListMap[ModelId, ModelSubscriber[_]]],
    componentSubscribers: Ref[MultiListMap[(ComponentId, String), ComponentSubscriber[_]]]
) {

  /** Tracked changes for models. */
  def iterationStream: ZStream[Any, Nothing, Chunk[ModelId]] = ZStream
    .fromHub(hub)
    .groupedWithin(100, 10.millis)

  /** Activate events for a component. */
  def activateEvents(node: TreeNode): Task[Unit] = {
    Logger.debug(s"Activating events on ${node.id}")
    for {
      _ <- disableOldEventsOnComponent(node.id)
      _ <- ZIO.collectAllDiscard {
             node.children.map(activateEvents)
           }
      _ <- ZIO.collectAllDiscard(
             node.assembly.bindings.map(activateBinding(node, _))
           )
    } yield {
      ()
    }
  }

  private def disableOldEventsOnComponent(id: ComponentId): Task[Unit] = {
    for {
      _ <- eventRegistry.cancel(id)
      _ <- disableModelEventsOnComponent(id)
      _ <- disableComponentEventsOnComponentId(id)
    } yield {
      ()
    }
  }

  private def disableModelEventsOnComponent(id: ComponentId): Task[Unit] = {
    modelSubscribers
      .modify { modelSubscribers =>
        val (alive, toGo) = modelSubscribers.partition { (_, v) =>
          v.owner != id
        }
        toGo -> alive
      }
      .flatMap { (removal: MultiListMap[ModelId, ModelSubscriber[_]]) =>
        ZIO.foreachDiscard(removal.values) {
          _.cancel()
        }
      }
  }

  private def disableComponentEventsOnComponentId(id: ComponentId): Task[Unit] = {
    componentSubscribers
      .modify { subscribers =>
        val (alive, toGo) = subscribers.partition { (k, v) =>
          // Only kick by subscriber (owner), but not by source, otherwise
          // Parents can't subscribe to children properly.
          /*k != id &&*/
          v.owner != id
        }
        toGo -> alive
      }
      .flatMap { (removal: MultiListMap[(ComponentId, String), ComponentSubscriber[_]]) =>
        ZIO.foreachDiscard(removal.values) {
          _.cancel()
        }
      }
  }

  private def activateBinding(node: TreeNode, eventBinding: EventBinding): Task[Unit] = {
    eventBinding match
      case EventBinding.SourceSink(source, sink) =>
        activateSourceSink(node, source, sink)
  }

  private def activateSourceSink[E](
      owner: TreeNode,
      source: EventSource[E],
      sink: EventSink[E]
  ): Task[Unit] = {
    sourceToStream(owner, source).flatMap { stream =>
      val handler = convertSink(owner, sink)
      stream
        .runForeach(handler)
        .forkZioLogged(s"Source Sink on ${owner.id}")
        .ignoreLogged
    }
  }

  private def sourceToStream[E](owner: TreeNode, source: EventSource[E]): Task[XStream[E]] = {
    source match
      case EventSource.ComponentEvent(event, id) =>
        val componentId = id.getOrElse(owner.id)
        repEventSource(owner, componentId, event)
      case EventSource.WindowJsEvent(js)         =>
        registeredJsEvent(owner.id, org.scalajs.dom.window, js.copy(preventDefault = false))
      case e: EventSource.WithState[_, _, _]     => {
        convertWithState(owner, e)
      }
      case m: EventSource.ModelChange[_]         => {
        convertModelChange(owner, m)
      }
      case e: EventSource.EffectEvent[_, _, _]   =>
        effectEvent(owner, e)
      case EventSource.MapSource(from, fn)       =>
        sourceToStream(owner, from).map(_.map(fn))
      case a: EventSource.AndSource[_]           =>
        andEvent(owner, a)
  }

  private def convertSink[E](node: TreeNode, sink: EventSink[E]): E => Task[Unit] = {
    sink match {
      case c: EventSink.ModelChange[_, _]  =>
        convertModelChangeSink(node, c)
      case EventSink.ExecuteCode(f)        =>
        input => ZIO.attempt(f(input))
      case EventSink.Multiple(sinks)       =>
        val converted = sinks.map(convertSink(node, _))
        input => {
          ZIO.collectAllDiscard {
            converted.map(_.apply(input))
          }
        }
      case t: EventSink.CustomEventSink[_] =>
        convertComponentTriggerSink(node, t)
    }
  }

  private def convertModelChangeSink[E, M](node: TreeNode, change: EventSink.ModelChange[E, M]): E => Task[Unit] = {
    input =>
      {
        for {
          subscribers <- modelSubscribers.get
          update      <- state.modify { state =>
                           val oldValue     = state.modelValues(change.model.id).asInstanceOf[M]
                           val updated      = change.f(input, oldValue)
                           Logger.trace(s"Model Change ${change.model.id} ${oldValue} -> ${updated}")
                           val updatedState = state.withModelValue(change.model.id, updated)
                           (oldValue, updated) -> updatedState
                         }
          _           <- {
            val subscriberList = subscribers.get(change.model.id)
            ZIO.collectAllDiscard {
              subscriberList.map { subscriber =>
                subscriber.asInstanceOf[ModelSubscriber[M]].onChange(update._1, update._2)
              }
            }
          }
          x           <- hub.offer(change.model.id)
        } yield {
          ()
        }
      }
  }

  private def convertComponentTriggerSink[E](
      node: TreeNode,
      trigger: EventSink.CustomEventSink[E]
  ): E => Task[Unit] = { input =>
    {
      val dstId = trigger.componentId.getOrElse(node.id)
      for {
        subscriberMap <- componentSubscribers.get
        subscribers    = subscriberMap.get(dstId -> trigger.event.name)
        _              =
          Logger.info(
            s"Will send ${input} on ${trigger.event.name} from ${node.id} to ${subscribers.size} receivers (owners=${subscribers
                .map(_.owner)})"
          )
        _             <- ZIO.collectAllDiscard(subscribers.map(_.asInstanceOf[ComponentSubscriber[E]].onEmit(input)))
      } yield {
        ()
      }
    }
  }

  private def repEventSource[E](owner: TreeNode, componentId: ComponentId, event: Event[E]): Task[XStream[E]] = {
    event match {
      case js: Event.JsEvent                    =>
        jsEventSource(componentId, js)
      case Event.MappedEvent(underlying, mapFn) =>
        repEventSource(owner, componentId, underlying).map(_.map(mapFn))
      case c: Event.Custom[E]                   =>
        convertComponentEvent(owner.id, componentId, c)
      case Event.Assembled                      =>
        ZIO.succeed(
          ZStream.succeed(())
        )
    }
  }

  private def jsEventSource(componentId: ComponentId, event: Event.JsEvent): Task[XStream[ScalaJsEvent]] = {
    locator.locate(componentId).flatMap { js =>
      registeredJsEvent(componentId, js, event)
    }
  }

  private def registeredJsEvent(
      componentId: ComponentId,
      scalaJsEventTarget: ScalaJsEventTarget,
      event: Event.JsEvent
  ): Task[XStream[ScalaJsEvent]] = {
    ZIO.succeed(
      eventRegistry
        .lift(componentId, scalaJsEventTarget, event)
        .tapError { e =>
          Logger.warnZio(e.getMessage)
        }
        .orDie
    )
  }

  private def effectEvent[E, F[_], R](
      node: TreeNode,
      event: EventSource.EffectEvent[E, F, R]
  ): Task[XStream[scala.util.Try[R]]] = {
    val decoder: F[R] => Task[R] = event.effectOperation.support.name match {
      case EffectSupport.FutureName => in => ZIO.fromFuture(_ => in.asInstanceOf[Future[R]])
      case EffectSupport.TaskName   => in => in.asInstanceOf[Task[R]]
      case other                    =>
        return ZIO.fail(new UnsupportedOperationException(s"Unexpected Effect Type ${other}"))
    }
    for {
      underlying <- sourceToStream(node, event.trigger)
    } yield {
      underlying.mapZIO { in =>
        val zio = decoder(event.effectOperation.fn(in))
        zio.fold(f => scala.util.Failure(f), x => scala.util.Success(x))
      }
    }
  }

  private def andEvent[E](
      node: TreeNode,
      and: EventSource.AndSource[E]
  ): Task[XStream[E]] = {
    // Note: All scope is missing here!
    for {
      underlying <- sourceToStream(node, and.binding.source)
      hub        <- zio.Hub.bounded[zio.stream.Take[Nothing, E]](1)
      _          <- underlying.runIntoHub(hub).forkZioLogged(s"and source into hub before ${node.id}")
      hubSource   = ZStream.fromHub(hub).flattenTake
      sink        = convertSink(node, and.binding.sink)
      _          <- hubSource.runForeach(sink).forkZioLogged(s"and hub into sink ${node.id}")
    } yield {
      hubSource
    }
  }

  private def convertWithState[E, F, S](
      node: TreeNode,
      withState: EventSource.WithState[E, F, S]
  ): Task[XStream[(E, S)]] = {
    sourceToStream(node, withState.inner).map { underlying =>
      val transformed = underlying
        .mapZIO { value =>
          locator.withRuntimeContext(withState.componentId) { ctx =>
            val elementState = withState.fetcher(withState.provider(ctx))
            value -> elementState
          }
        }
        .tapError(e => Logger.warnZio(e.getMessage))
        .orDie
      transformed
    }
  }

  private def convertModelChange[M](
      node: TreeNode,
      eventSource: EventSource.ModelChange[M]
  ): Task[XStream[(M, M)]] = {
    ZIO.succeed {
      ZStream.asyncZIO { callback =>
        object handler extends ModelSubscriber[M] {
          override def onChange(from: M, to: M): UIO[Unit] = {
            ZIO.succeed(
              callback.single(from -> to)
            )
          }

          override def cancel(): UIO[Unit] = {
            ZIO.succeed(
              callback.end
            )
          }

          override def owner: ComponentId = node.id
        }
        modelSubscribers.update(_.add(eventSource.model.id, handler))
      }
    }
  }

  private def convertComponentEvent[E](
      ownerComponentId: ComponentId,
      sourceComponentId: ComponentId,
      event: Event.Custom[E]
  ): Task[XStream[E]] = {
    Logger.trace(s"Subscribing ${ownerComponentId} to ${sourceComponentId} in components")
    ZIO.succeed {
      ZStream.asyncZIO { callback =>
        object entry extends ComponentSubscriber[E] {
          override def owner: ComponentId = ownerComponentId

          override def cancel(): UIO[Unit] = {
            Logger.debug(s"Canceling ${sourceComponentId} -> ${ownerComponentId} on ${event.name}")
            ZIO.succeed(
              callback.end
            )
          }

          override def onEmit(data: E): UIO[Unit] = {
            ZIO.succeed(
              callback.single(data)
            )
          }
        }
        for {
          _       <- Logger
                       .infoZio(s"Subscribing ${sourceComponentId}/${event.name} --> ${ownerComponentId} in components")
                       .orDie
          updated <- componentSubscribers.update(_.add(sourceComponentId -> event.name, entry))
        } yield {
          ()
        }
      }
    }
  }

  /** Remove all not referenced bindings. */
  def garbageCollect(referencedComponentIds: Set[ComponentId], referencedModelIds: Set[ModelId]): Task[Unit] = {
    // TODO: Event Target removal?!
    for {
      _ <-
        Logger.debugZio(
          s"Garbage collect, referenced components: ${referencedComponentIds.toSeq.map(_.id).sorted} / models: ${referencedModelIds.toSeq.map(_.id).sorted}"
        )
      _ <- garbageCollectModelSubscribers(referencedModelIds)
      _ <- garbageCollectComponentSubscribers(referencedComponentIds)
      _ <- eventRegistry.cancelUnreferenced(referencedComponentIds)
    } yield {
      ()
    }
  }

  private def garbageCollectModelSubscribers(referencedModelIds: Set[ModelId]): Task[Unit] = {
    modelSubscribers
      .modify { modelSubscribers =>
        val (alive, toGo) = modelSubscribers.partitionKeys(id => referencedModelIds.contains(id))
        toGo -> alive
      }
      .flatMap { (removal: MultiListMap[ModelId, ModelSubscriber[_]]) =>
        ZIO.foreachDiscard(removal.values) {
          _.cancel()
        }
      }
  }

  private def garbageCollectComponentSubscribers(referencedComponents: Set[ComponentId]): Task[Unit] = {
    componentSubscribers
      .modify { subscribers =>
        val (alive, toGo) = subscribers.partition { (k, v) =>
          referencedComponents.contains(k._1) && referencedComponents.contains(v.owner)
        }
        toGo -> alive
      }
      .flatMap { (removal: MultiListMap[(ComponentId, String), ComponentSubscriber[_]]) =>
        ZIO.foreachDiscard(removal.values) {
          _.cancel()
        }
      }
  }
}

object EventManager {

  trait ModelSubscriber[M] {
    def onChange(from: M, to: M): UIO[Unit]
    def cancel(): UIO[Unit]

    def owner: ComponentId
  }

  trait ComponentSubscriber[E] {
    def owner: ComponentId
    def cancel(): UIO[Unit]
    def onEmit(data: E): UIO[Unit]

    override def toString: String = {
      s"ComponentSubscriber(${owner})"
    }
  }

  /** Helper for locating elements and providing runtime contexts. */
  trait Locator {

    protected def unsafeLocate(id: ComponentId): ScalaJsElement

    def locate(id: ComponentId): Task[ScalaJsElement] = {
      ZIO.attempt(unsafeLocate(id))
    }

    def withRuntimeContext[T](id: ComponentId)(f: RuntimeContext => T): Task[T] = {
      for {
        context <- ZIO.attempt(unsafeCreateRuntimeContext(id))
        result  <- ZIO.attempt(f(context))
      } yield result
    }

    protected def unsafeCreateRuntimeContext(id: ComponentId): RuntimeContext = new RuntimeContext {
      override val jsElement: ScalaJsElement = unsafeLocate(id)

      override def jump(componentId: ComponentId): RuntimeContext = {
        unsafeCreateRuntimeContext(componentId)
      }
    }
  }

  def create(state: Ref[AssemblyState], locator: Locator): Task[EventManager] = {
    for {
      hub                  <- Hub.bounded[ModelId](256)
      eventRegistry        <- JsEventRegistry.create[ComponentId]
      eventSubscribers     <- Ref.make(MultiListMap.empty[ModelId, ModelSubscriber[_]])
      componentSubscribers <- Ref.make(MultiListMap.empty[(ComponentId, String), ComponentSubscriber[_]])
    } yield {
      new EventManager(locator, hub, state, eventRegistry, eventSubscribers, componentSubscribers)
    }
  }

  case class Iteration(
      state: AssemblyState,
      changedModels: Set[ModelId]
  )
}
