package kreuzberg.engine.naive

import kreuzberg.*
import kreuzberg.engine.naive.utils.MutableMultimap
import kreuzberg.dom.*
import kreuzberg.engine.common.{ModelValues, TreeNode}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.ref.WeakReference
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Encapsulate the highly stateful event handling. */
class EventManager(delegate: EventManagerDelegate)(using ServiceRepository) {

  /** A Pending change. */
  private sealed trait PendingChange

  private case class PendingModelChange[M](model: Model[M], fn: M => M) extends PendingChange

  /** There is a next iteration triggered yet */
  private var _hasNextIteration: Boolean = false

  /** We are in a next iteration. */
  private var _inIteration: Boolean = false

  /** During Iteration: Set of changed models. */
  private val _changedModel = mutable.Set[Identifier]()

  /** Bindings to Model instances. */
  private case class ModelBindings[T](
      handler: (T, T) => Unit,
      owner: Identifier
  )

  /**
   * Bindings to Window-Events. We cannot directly move them to the inner handler, as we need a simple way to deconnect
   * garbage collected / dereferenced components from it.
   */
  private case class WindowEventBinding(
      handler: ScalaJsEvent => Unit,
      owner: Identifier
  )

  /** Binding to Component Events. */
  private case class ComponentEventBinding[T](
      handler: T => Unit,
      owner: Identifier
  )

  /** Bindings to channel events. */
  private case class ChannelBinding[T](
      handler: T => Unit,
      owner: Identifier
  )

  private case class RegisteredTimer(
      stopper: () => Unit
  )

  /** Binding to not own object. */
  private case class ForeignBinding[T](
      jsEvent: JsEvent[T],
      sink: T => Unit,
      owner: Identifier
  )

  /** Pending Changes. */
  private val _pending = new mutable.Queue[PendingChange]()

  /**
   * Bindings for foreign components Key = affected component
   */
  private val _foreignBindings = new MutableMultimap[Identifier, ForeignBinding[_]]()

  /** Bindings to Window Events. */
  private val _windowEventBindings = new MutableMultimap[String, WindowEventBinding]()

  /** Bindings to channels. */
  private val _channelBindings = new MutableMultimap[Identifier, ChannelBinding[_]]()

  /** Timers with owners. */
  private val _registeredTimers = new MutableMultimap[Identifier, RegisteredTimer]()

  // Keeping track of registered window events (we do not yet remove them)
  private val _registeredWindowEvents = mutable.Set[String]()

  private var _currentState = ModelValues()

  /** Activate Events on a Node. */
  def activateEvents(node: TreeNode): Unit = {
    // Drop Existing model bindings
    _windowEventBindings.filterValuesInPlace(_.owner != node.id)
    _channelBindings.filterValuesInPlace(_.owner != node.id)
    _registeredTimers.deregisterKey(node.id)(_.stopper())
    _foreignBindings.filterValuesInPlace(_.owner != node.id)

    node.children.foreach {
      activateEvents
    }
    node.handlers.foreach(activateEvent(node, _))

    _foreignBindings.foreachKey(node.id) { foreign =>
      val source = delegate.locate(node.id)
      bindJsEvent(source, foreign.jsEvent, foreign.sink)
    }
  }

  def clear(): Unit = {
    Logger.debug("Full Clear")
    _changedModel.clear()
  }

  def garbageCollect(referencedComponents: Set[Identifier], referencedModels: Set[Identifier]): Unit = {
    _windowEventBindings.filterValuesInPlace(binding => referencedComponents.contains(binding.owner))
    _channelBindings.filterValuesInPlace(binding => referencedComponents.contains(binding.owner))
    _foreignBindings.filterValuesInPlace(binding => referencedComponents.contains(binding.owner))
    _registeredTimers.deregisterKeys(c => !referencedComponents.contains(c))(_.stopper())
  }

  private def activateEvent(node: TreeNode, eventBinding: EventBinding): Unit = {
    eventBinding match {
      case s: EventBinding.SourceSink[_] => activateSourceSinkBinding(node, s)
    }
  }

  private def activateSourceSinkBinding[E](
      node: TreeNode,
      sourceSink: EventBinding.SourceSink[E]
  ): Unit = {
    val transformedSink = transformSink(node, sourceSink.sink)
    bindEventSource(node, sourceSink.source, transformedSink)
  }

  private def transformSink[T](node: TreeNode, eventSink: EventSink[T]): T => Unit = {
    eventSink match
      case EventSink.ModelChange(modelId, f)                 =>
        eventData =>
          val change = PendingModelChange(
            modelId,
            fn = f(eventData, _)
          )
          _pending.append(change)
          ensureNextIteration()
      case EventSink.ChannelSink(channel)                    =>
        eventData => triggerChannel(channel, eventData)
      case EventSink.ExecuteCode(f)                          => f
      case EventSink.SetProperty(property)                   =>
        eventData => {
          updateJsProperty(eventData, property)
        }
      case EventSink.PreTransformer(underlying, transformer) =>
        preTransformSink(node, transformer, underlying)
  }

  private def preTransformSink[E, F](
      node: TreeNode,
      transformer: EventTransformer[E, F],
      sink: EventSink[F]
  ): E => Unit = {
    val underlying = transformSink(node, sink)
    buildTransformer(node, transformer, underlying)
  }

  private def buildTransformer[E, F](
      node: TreeNode,
      transformer: EventTransformer[E, F],
      underlying: F => Unit
  ): E => Unit = {
    transformer match {
      case EventTransformer.Map(fn)                 => in => underlying(fn(in))
      case EventTransformer.Collect(fn)             => in => if (fn.isDefinedAt(in)) { underlying(fn(in)) }
      case EventTransformer.Tapped(fn)              =>
        in => {
          try {
            fn(in)
          } catch {
            case NonFatal(e) =>
              Logger.warn(s"Error in tap: ${e.getMessage}")
          }
          underlying(in)
        }
      case EventTransformer.WithState(runtimeState) => { in =>
        try {
          val state = fetchStateUnsafe(runtimeState)
          underlying((in, state))
        } catch {
          case NonFatal(e) =>
            Logger.warn(s"Error fetching runtime state: ${e.getMessage}")
        }
      }
      case EventTransformer.WithEffect(effectFn)    => { in =>
        effectFn(in).fn(implicitly[ExecutionContext]).andThen { case result =>
          underlying((in, result))
        }
      }
      case EventTransformer.TryUnpack1(failureSink) => {
        val failureSinkTransformed = transformSink(node, failureSink)
        in => {
          in match {
            case Success(ok)      => underlying(ok)
            case Failure(failure) => failureSinkTransformed(failure)
          }
        }
      }
      case EventTransformer.TryUnpack2(failureSink) => {
        val failureSinkTransformed = transformSink(node, failureSink)
        in => {
          in match {
            case (v, Success(ok))      => underlying(v -> ok)
            case (v, Failure(failure)) => failureSinkTransformed(v -> failure)
          }
        }
      }
      case EventTransformer.And(other)              => {
        val othersTransformed = transformSink(node, other)
        in => {
          othersTransformed.apply(in)
          underlying(in)
        }
      }
    }
  }

  private def triggerChannel[T](ref: WeakReference[Channel[T]], data: T): Unit = {
    ref.get match {
      case Some(c) =>
        _channelBindings.foreachKey(c.id) { _.asInstanceOf[ChannelBinding[T]].handler(data) }
      case None    => // nothing
    }
  }

  private def bindEventSource[E](ownNode: TreeNode, eventSource: EventSource[E], sink: E => Unit): Unit = {
    eventSource match
      case j: EventSource.Js[_]                             =>
        j.jsEvent.componentId match {
          case None              =>
            val handler: ScalaJsEvent => Unit = { in =>
              try {
                val transformed = j.jsEvent.fn(in)
                sink(transformed)
              } catch {
                case NonFatal(e) =>
                  Logger.warn(s"Exception during window JS Event by component ${ownNode.id}: ${e}")
              }
            }
            _windowEventBindings.add(
              j.jsEvent.name,
              WindowEventBinding(handler, ownNode.id)
            )
            val existing                      = _registeredWindowEvents.contains(j.jsEvent.name)
            if (!existing) {
              // The only place to register it, we won't deregister yet
              // TODO: Window event deregistration, KRZ-124
              bindJsEvent(
                org.scalajs.dom.window,
                j.jsEvent.copy(fn = identity, capture = false),
                event => onWindowEvent(j.jsEvent.name, event)
              )
              _registeredWindowEvents.add(j.jsEvent.name)
              Logger.debug(s"Fresh bound ${j.jsEvent.name} on window")
            }
          case Some(componentId) =>
            val source = delegate.locate(componentId)
            if (ownNode.id != componentId) {
              _foreignBindings.add(
                componentId,
                ForeignBinding(
                  jsEvent = j.jsEvent,
                  sink = sink,
                  owner = ownNode.id
                )
              )
            }
            bindJsEvent(source, j.jsEvent, sink)
        }
      case EventSource.Assembled                            =>
        scalajs.js.timers.setTimeout(0) {
          sink(())
        }
      case e: EventSource.EffectEvent[_, _]                 =>
        bindEffect(ownNode, e, sink)
      case a: EventSource.AndSource[_]                      =>
        bindAnd(ownNode, a, sink)
      case c: EventSource.ChannelSource[_]                  =>
        bindChannel(ownNode, c, sink)
      case o: EventSource.OrSource[_]                       =>
        bindEventSource(ownNode, o.left, sink)
        bindEventSource(ownNode, o.right, sink)
      case t: EventSource.Timer                             =>
        val timer = if (t.periodic) {
          val handle = scalajs.js.timers.setInterval(t.duration) { sink(()) }
          RegisteredTimer(
            stopper = () => scalajs.js.timers.clearInterval(handle)
          )
        } else {
          val handle = scalajs.js.timers.setTimeout(t.duration) { sink(()) }
          RegisteredTimer(
            stopper = () => scalajs.js.timers.clearTimeout(handle)
          )
        }
        _registeredTimers.add(ownNode.id, timer)
      case EventSource.PostTransformer(source, transformer) =>
        val transformedSink = buildTransformer(ownNode, transformer, sink)
        bindEventSource(ownNode, source, transformedSink)
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
      case RuntimeState.Const(value)                 => {
        value
      }
      case RuntimeState.Collect(from)                => {
        from.map(fetchStateUnsafe)
      }
  }

  private def updateJsProperty[R <: ScalaJsElement, S](value: S, p: RuntimeState.JsProperty[R, S]): Unit = {
    p.setter(delegate.locate(p.componentId).asInstanceOf[R], value)
  }

  private def fetchJsRuntimeStateUnsafe[R <: ScalaJsElement, S](s: RuntimeState.JsRuntimeStateBase[R, S]): S = {
    s.getter(delegate.locate(s.componentId).asInstanceOf[R])
  }

  private def bindEffect[E, F[_], R](
      own: TreeNode,
      event: EventSource.EffectEvent[E, R],
      sink: ((E, Try[R])) => Unit
  ): Unit = {
    bindEventSource(
      own,
      event.trigger,
      v =>
        event.effectOperation(v).fn(implicitly[ExecutionContext]).andThen { case result =>
          sink((v, result))
        }
    )
  }

  private def bindAnd[T](ownNode: TreeNode, event: EventSource.AndSource[T], sink1: T => Unit): Unit = {
    val sink0               = transformSink(ownNode, event.binding.sink)
    val combined: T => Unit = { value =>
      sink0(value)
      sink1(value)
    }
    bindEventSource(ownNode, event.binding.source, combined)
  }

  private def bindChannel[T](ownNode: TreeNode, event: EventSource.ChannelSource[T], sink: T => Unit): Unit = {
    event.channel.get.foreach { c =>
      _channelBindings.add(c.id, ChannelBinding(sink, ownNode.id))
    }
  }

  private def bindJsEvent[E](
      source: org.scalajs.dom.EventTarget,
      event: JsEvent[E],
      sink: E => Unit
  ): Unit = {
    source.addEventListener(
      event.name,
      { (e: ScalaJsEvent) =>
        try {

          Logger.debug(s"Reacting to ${event.name} (capture=${event.capture})")
          val transformed = event.fn(e)
          sink(transformed)
        } catch {
          case NonFatal(e) => Logger.warn(s"Exception on JS Event ${event.name}: ${e}}")
        }
      },
      event.capture
    )
  }

  private def ensureNextIteration(): Unit = {
    if (!_hasNextIteration && !_inIteration) {
      _hasNextIteration = true
      scalajs.js.timers.setTimeout(0) {
        onNextIteration()
      }
    }
  }

  /** Run the next iteration. */
  private def onNextIteration(): Unit = {
    Logger.debug("Starting Iteration")
    _currentState = delegate.modelValues
    _hasNextIteration = false
    _inIteration = true
    _changedModel.clear()
    val max = 10000
    var it  = 0
    while (_pending.nonEmpty && it < max) {
      val first = _pending.dequeue()
      try {
        handlePendingChange(first)
      } catch {
        case NonFatal(e) =>
          Logger.debug(s"Error in iteration: ${e.getMessage}")
      }
      it += 1
    }
    if (it >= max) {
      Logger.debug(s"Got more than ${max} iterations, giving up")
    }
    Logger.debug(s"End Iteration, changed models: ${_changedModel}")
    _inIteration = false
    delegate.onIterationEnd(_currentState, _changedModel.toSet)
  }

  private def handlePendingChange(change: PendingChange): Unit = {
    change match {
      case p: PendingModelChange[_] =>
        handlePendingModelChange(p)
    }
  }

  private def handlePendingModelChange[T](change: PendingModelChange[T]): Unit = {
    val value   = _currentState.value(change.model)
    val updated = change.fn(value)
    if (value != updated) {
      Logger.debug(
        s"Updating ${change.model.id} from ${value} to ${updated}"
      )
      _currentState = _currentState.withModelValue(change.model.id, updated)
      _changedModel.add(change.model.id)
    }
  }

  private def onWindowEvent(name: String, event: ScalaJsEvent): Unit = {
    Logger.debug(s"OnWindowEvent ${name}: Handlers: ${_windowEventBindings.sizeForKey(name)}")
    _windowEventBindings.foreachKey(name) { binding =>
      binding.handler(event)
    }
  }
}
