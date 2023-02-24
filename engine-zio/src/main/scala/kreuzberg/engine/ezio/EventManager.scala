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
          k != id && v.owner != id
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
        .tap { e =>
          Logger.debugZio(s"Signal on source: ${owner.id} ${e}")
        }
        .runForeach(handler)
        .fork
        .ignoreLogged
    }
  }

  private def sourceToStream[E](owner: TreeNode, source: EventSource[E]): Task[XStream[E]] = {
    source match
      case EventSource.RepEvent(rep, event) => repEventSource(owner, rep, event)
      case EventSource.OwnEvent(event)      => repEventSource(owner, owner, event)
      case EventSource.WindowJsEvent(js)    =>
        registeredJsEvent(owner, org.scalajs.dom.window, js.copy(preventDefault = false))
      case e: EventSource.WithState[_, _]   => {
        convertWithState(owner, e)
      }
      case m: EventSource.ModelChange[_]    => {
        convertModelChange(owner, m)
      }
      case e: EventSource.EffectEvent[_, _] =>
        effectEvent(owner, e)
      case EventSource.MapSource(from, fn)  =>
        sourceToStream(owner, from).map(_.map(fn))
      case a: EventSource.AndSource[_]      =>
        andEvent(owner, a)
  }

  private def convertSink[E](node: TreeNode, sink: EventSink[E]): E => Task[Unit] = {
    sink match {
      case c: EventSink.ModelChange[_, _]        =>
        convertModelChangeSink(node, c)
      case EventSink.Custom(f)                   =>
        input => ZIO.attempt(f(input))
      case EventSink.Multiple(sinks)             =>
        val converted = sinks.map(convertSink(node, _))
        input => {
          ZIO.collectAllDiscard {
            converted.map(_.apply(input))
          }
        }
      case t: EventSink.TriggerComponentEvent[_] =>
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
                           Logger.debug(s"Model Change ${change.model.id} ${oldValue} -> ${updated}")
                           val updatedState = state.withModelValue(change.model.id, updated)
                           (oldValue, updated) -> updatedState
                         }
          _           <- Logger.debugZio("Telling Subscribers...")
          _           <- {
            val subscriberList = subscribers.get(change.model.id)
            ZIO.collectAllDiscard {
              subscriberList.map { subscriber =>
                subscriber.asInstanceOf[ModelSubscriber[M]].onChange(update._1, update._2)
              }
            }
          }
          -           <- Logger.debugZio("Offering...")
          x           <- hub.offer(change.model.id)
          _           <- Logger.debugZio(s"Offered: ${x}...")
        } yield {
          ()
        }
      }
  }

  private def convertComponentTriggerSink[E](
      node: TreeNode,
      trigger: EventSink.TriggerComponentEvent[E]
  ): E => Task[Unit] = { input =>
    {
      for {
        subscriberMap <- componentSubscribers.get
        subscribers    = subscriberMap.get(node.id -> trigger.componentEvent.name)
        _              = Logger.info(s"Will send ${input} to ${subscribers.size} receivers (owners=${subscribers.map(_.owner)})")
        _             <- ZIO.collectAllDiscard(subscribers.map(_.asInstanceOf[ComponentSubscriber[E]].onEmit(input)))
      } yield {
        ()
      }
    }
  }

  private def repEventSource[E](owner: TreeNode, source: TreeNode, event: Event[E]): Task[XStream[E]] = {
    event match {
      case js: Event.JsEvent                    =>
        jsEventSource(source, js)
      case Event.MappedEvent(underlying, mapFn) =>
        repEventSource(source, source, underlying).map(_.map(mapFn))
      case c: Event.ComponentEvent[E]           =>
        convertComponentEvent(owner.id, source.id, c)
      case Event.Assembled                      =>
        ZIO.succeed(
          ZStream.succeed(())
        )
    }
  }

  private def jsEventSource(node: TreeNode, event: Event.JsEvent): Task[XStream[ScalaJsEvent]] = {
    locator(node.id).flatMap { js =>
      registeredJsEvent(node, js, event)
    }
  }

  private def registeredJsEvent(
      node: TreeNode,
      scalaJsEventTarget: ScalaJsEventTarget,
      event: Event.JsEvent
  ): Task[XStream[ScalaJsEvent]] = {
    ZIO.succeed(
      eventRegistry
        .lift(node.id, scalaJsEventTarget, event)
        .tapError { e =>
          Logger.warnZio(e.getMessage)
        }
        .orDie
    )
  }

  private def effectEvent[E, F](
      node: TreeNode,
      event: EventSource.EffectEvent[E, F]
  ): Task[XStream[scala.util.Try[F]]] = {
    for {
      underlying <- sourceToStream(node, event.trigger)
    } yield {
      underlying.mapZIO { in =>
        val zio = ZIO.fromFuture(ec => event.effectOperation.fn(in, ec))
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
      _          <- underlying.runIntoHub(hub).fork
      hubSource   = ZStream.fromHub(hub).flattenTake
      sink        = convertSink(node, and.binding.sink)
      _          <- hubSource.runForeach(sink).fork
    } yield {
      hubSource
    }
  }

  private def convertWithState[E, F](
      node: TreeNode,
      withState: EventSource.WithState[E, F]
  ): Task[XStream[(E, F)]] = {
    sourceToStream(node, withState.inner).map { underlying =>
      val transformed = underlying
        .mapZIO { value =>
          locator(withState.from).map { js =>
            val elementState = withState.getter.get(js)
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
      event: Event.ComponentEvent[E]
  ): Task[XStream[E]] = {
    Logger.debug(s"Subscribing ${ownerComponentId} to ${sourceComponentId} in components")
    ZIO.succeed {
      ZStream.asyncZIO { callback =>
        object entry extends ComponentSubscriber[E] {
          override def owner: ComponentId = ownerComponentId

          override def cancel(): UIO[Unit] = {
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
        Logger.debug(s"Subscribing ${ownerComponentId} to ${sourceComponentId} in components (update phase)")
        componentSubscribers.update(_.add(sourceComponentId -> event.name, entry))
      }
    }
  }

  /** Remove all not referenced bindings. */
  def garbageCollect(referencedComponentIds: Set[ComponentId], referencedModelIds: Set[ModelId]): Task[Unit] = {
    // TODO: Event Target removal?!
    for {
      _ <- Logger.debugZio(
             s"Garbage collect, referenced components: ${referencedComponentIds} / models: ${referencedModelIds}"
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
  }

  // TODO: Replace by Environment
  type Locator = ComponentId => Task[ScalaJsElement]

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
