package kreuzberg.engine.ezio

import kreuzberg.*
import kreuzberg.dom.*
import kreuzberg.engine.common.{ModelValues, TreeNode}
import kreuzberg.engine.ezio.EventManager.{ChannelSubscriber, ComponentSubscriber, Iteration, Locator, create}
import kreuzberg.engine.ezio.utils.MultiListMap
import zio.stream.{ZSink, ZStream}
import zio.*

import scala.concurrent.Future
import scala.ref.WeakReference

type XStream[T] = ZStream[Any, Nothing, T]

/** Responsible for activating Events on components and putting them all into one single Iteration Stream. */
class EventManager(
    locator: Locator,
    hub: Hub[Identifier],
    modelValues: Ref[ModelValues],
    eventRegistry: JsEventRegistry[Identifier],
    channelSubscribers: Ref[MultiListMap[Identifier, ChannelSubscriber[_]]]
) {

  /** Tracked changes for models. */
  def iterationStream: ZStream[Any, Nothing, Chunk[Identifier]] = ZStream
    .fromHub(hub)
    .groupedWithin(100, 10.millis)

  /** Activate events for a component. */
  def activateEvents(node: TreeNode): Task[Unit] = {
    Logger.trace(s"Activating events on ${node.id}/${node.component.comment}")
    for {
      _ <- disableOldEventsOnComponent(node.id)
      _ <- ZIO.collectAllDiscard {
             node.children.map(activateEvents)
           }
      _ <- ZIO.collectAllDiscard(
             node.handlers.map(activateBinding(node, _))
           )
    } yield {
      ()
    }
  }

  private def disableOldEventsOnComponent(id: Identifier): Task[Unit] = {
    for {
      _ <- eventRegistry.cancel(id)
      _ <- disableChannelEventsOnComponentId(id)
    } yield {
      ()
    }
  }

  private def disableChannelEventsOnComponentId(id: Identifier): Task[Unit] = {
    channelSubscribers
      .modify { subscribers =>
        val (alive, toGo) = subscribers.partition { (_, v) =>
          v.owner != id
        }
        toGo -> alive
      }
      .flatMap { (removal: MultiListMap[Identifier, ChannelSubscriber[_]]) =>
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
      case j: EventSource.Js[_]                =>
        j.jsEvent.componentId match {
          case None              => registeredJsEvent(owner.id, org.scalajs.dom.window, j.jsEvent)
          case Some(componentId) =>
            locator.locate(componentId).flatMap { js =>
              registeredJsEvent(componentId, js, j.jsEvent)
            }
        }
      case EventSource.Assembled               =>
        ZIO.succeed(
          ZStream.succeed(())
        )
      case e: EventSource.WithState[_, _]      => {
        convertWithState(owner, e)
      }
      case e: EventSource.EffectEvent[_, _, _] =>
        effectEvent(owner, e)
      case EventSource.MapSource(from, fn)     =>
        sourceToStream(owner, from).map(_.map(fn))
      case EventSource.CollectEvent(from, fn)  =>
        sourceToStream(owner, from).map(_.collect(fn))
      case a: EventSource.AndSource[_]         =>
        andEvent(owner, a)
      case c: EventSource.ChannelSource[_]     =>
        convertChannelSource(owner, c)
      case o: EventSource.OrSource[_]          =>
        for {
          left  <- sourceToStream(owner, o.left)
          right <- sourceToStream(owner, o.right)
        } yield {
          left.merge(right)
        }
      case o: EventSource.TapSource[_]         =>
        sourceToStream(owner, o.inner).map { stream =>
          stream.tap { element =>
            ZIO.attempt(o.fn(element)).ignoreLogged
          }
        }
      case t: EventSource.Timer                =>
        ZIO.succeed(timeEvent(owner, t))
  }

  private def convertSink[E](node: TreeNode, sink: EventSink[E]): E => Task[Unit] = {
    sink match {
      case c: EventSink.ModelChange[_, _]          =>
        convertModelChangeSink(node, c)
      case EventSink.ExecuteCode(f)                =>
        input => ZIO.attempt(f(input))
      case EventSink.Multiple(sinks)               =>
        val converted = sinks.map(convertSink(node, _))
        input => {
          ZIO.collectAllDiscard {
            converted.map(_.apply(input))
          }
        }
      case EventSink.ContraCollect(underlying, pf) =>
        val converted = convertSink(node, underlying)
        input => {
          if (pf.isDefinedAt(input)) {
            converted(pf(input))
          } else {
            ZIO.unit
          }
        }
      case EventSink.ContraMap(underlying, f)      =>
        val converted = convertSink(node, underlying)
        input => {
          converted(f(input))
        }
      case c: EventSink.ChannelSink[_]             =>
        convertChannelSink(node, c)
      case s: EventSink.SetProperty[_, _]          =>
        convertSetProperty(node, s)
    }
  }

  private def convertModelChangeSink[E, M](node: TreeNode, change: EventSink.ModelChange[E, M]): E => Task[Unit] = {
    input =>
      {
        for {
          _ <- modelValues.modify { state =>
                 val oldValue     = state.value(change.model)
                 val updated      = change.f(input, oldValue)
                 Logger.trace(s"Model Change ${change.model.id} ${oldValue} -> ${updated}")
                 val updatedState = state.withModelValue(change.model.id, updated)
                 (oldValue, updated) -> updatedState
               }
          _ <- hub.offer(change.model.id)
        } yield {
          ()
        }
      }
  }

  private def convertChannelSink[E](
      node: TreeNode,
      sink: EventSink.ChannelSink[E]
  ): E => Task[Unit] = { input =>
    callChannelSink(sink.channel, input)
  }

  private def callChannelSink[E](channel: WeakReference[Channel[E]], input: E): Task[Unit] = {
    channel.get match {
      case None        => ZIO.unit
      case Some(found) =>
        for {
          subscribersMap <- channelSubscribers.get
          subscribers     = subscribersMap.get(found.id)
          _              <-
            Logger.traceZio(
              s"Will send ${input} on ${found} to ${subscribers.size} receivers (owners=${subscribers
                  .map(_.owner)})"
            )
          _              <- ZIO.collectAllDiscard(subscribers.map(_.asInstanceOf[ChannelSubscriber[E]].onEmit(input)))
        } yield {
          ()
        }
    }
  }

  private def convertSetProperty[R <: ScalaJsElement, E](
      node: TreeNode,
      sink: EventSink.SetProperty[R, E]
  ): E => Task[Unit] = { input =>
    ZIO.attempt {
      val node = locator.unsafeLocate(sink.property.componentId).asInstanceOf[R]
      sink.property.setter(node, input)
    }
  }

  private def registeredJsEvent[E](
      componentId: Identifier,
      scalaJsEventTarget: ScalaJsEventTarget,
      event: JsEvent[E]
  ): Task[XStream[E]] = {
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

  private def timeEvent(
      owner: TreeNode,
      timer: EventSource.Timer
  ): XStream[Unit] = {
    eventRegistry.timer(owner.id, timer.duration, periodic = timer.periodic).tapError { e =>
      Logger.warnZio(e.getMessage)
    }.orDie
  }

  private def convertWithState[E, S](
      node: TreeNode,
      withState: EventSource.WithState[E, S]
  ): Task[XStream[(E, S)]] = {
    sourceToStream(node, withState.inner).map { underlying =>
      val transformed = underlying
        .mapZIO { value =>
          for {
            state <- fetchState(withState.runtimeState)
          } yield {
            value -> state
          }
        }
        .tapError(e => Logger.warnZio(e.getMessage))
        .orDie
      transformed
    }
  }

  private def fetchState[S](s: RuntimeState[S]): Task[S] = {
    ZIO.attempt(fetchStateUnsafe(s))
  }

  private def fetchStateUnsafe[S](s: RuntimeState[S]): S = {
    s match
      case js: RuntimeState.JsRuntimeStateBase[_, _] => {
        fetchJsRuntimeStateUnsafe(js)
      }
      case RuntimeState.And(left, right)             => {
        (fetchStateUnsafe(left), fetchStateUnsafe(right))
      }
      case RuntimeState.Mapping(from, mapFn)         => {
        mapFn(fetchStateUnsafe(from))
      }
  }

  private def fetchJsRuntimeStateUnsafe[R <: ScalaJsElement, S](s: RuntimeState.JsRuntimeStateBase[R, S]): S = {
    s.getter(locator.unsafeLocate(s.componentId).asInstanceOf[R])
  }

  private def convertChannelSource[E](
      node: TreeNode,
      channelSource: EventSource.ChannelSource[E]
  ): Task[XStream[E]] = {
    ZIO.succeed {
      channelSource.channel.get match {
        case None    => ZStream.empty
        case Some(c) =>
          ZStream.asyncZIO { callback =>
            object handler extends ChannelSubscriber[E] {
              override def owner: Identifier = node.id

              override def cancel(): UIO[Unit] = ZIO.succeed(callback.end)

              override def onEmit(data: E): UIO[Unit] = ZIO.succeed(callback.single(data))
            }
            channelSubscribers.update(_.add(c.id, handler))
          }
      }
    }
  }

  /** Remove all not referenced bindings. */
  def garbageCollect(referencedComponentIds: Set[Identifier], referencedModelIds: Set[Identifier]): Task[Unit] = {
    // TODO: Event Target removal?!
    for {
      _ <-
        Logger.traceZio(
          s"Garbage collect, referenced components: ${referencedComponentIds.toSeq.map(_.value).sorted} / models: ${referencedModelIds.toSeq.map(_.value).sorted}"
        )
      _ <- eventRegistry.cancelUnreferenced(referencedComponentIds)
    } yield {
      ()
    }
  }
}

object EventManager {

  trait ModelSubscriber[M] {
    def onChange(from: M, to: M): UIO[Unit]
    def cancel(): UIO[Unit]

    def owner: Identifier
  }

  trait ComponentSubscriber[E] {
    def owner: Identifier
    def cancel(): UIO[Unit]
    def onEmit(data: E): UIO[Unit]

    override def toString: String = {
      s"ComponentSubscriber(${owner})"
    }
  }

  trait ChannelSubscriber[E] {
    def owner: Identifier
    def cancel(): UIO[Unit]
    def onEmit(data: E): UIO[Unit]
  }

  /** Helper for locating elements and providing runtime contexts. */
  trait Locator {
    def unsafeLocate(id: Identifier): ScalaJsElement

    def locate(id: Identifier): Task[ScalaJsElement] = {
      ZIO.attempt(unsafeLocate(id))
    }
  }

  def create(state: Ref[ModelValues], locator: Locator): Task[EventManager] = {
    for {
      hub                <- Hub.bounded[Identifier](256)
      eventRegistry      <- JsEventRegistry.create[Identifier]
      channelSubscribers <- Ref.make(MultiListMap.empty[Identifier, ChannelSubscriber[_]])
    } yield {
      new EventManager(locator, hub, state, eventRegistry, channelSubscribers)
    }
  }

  case class Iteration(
      state: ModelValues,
      changedModels: Set[Identifier]
  )
}
