package kreuzberg.engine.naive

import kreuzberg.*
import kreuzberg.engine.naive.utils.MutableMultimap
import org.scalajs.dom.{Element, Event}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.ref.WeakReference
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Encapsulate the highly stateful event handling. */
private[kreuzberg] class EventManager(delegate: EventManagerDelegate)(using sp: ServiceRepository) {

  /** A Pending change. */
  private sealed trait PendingChange

  private case class PendingModelChange[M](model: Model[M], fn: M => M) extends PendingChange

  /** A Pending Callback. */
  private case class Callback(fn: () => Unit) extends PendingChange

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
      handler: Event => Unit,
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
  private case class ForeignBinding(
      jsEvent: JsEvent,
      sink: Event => Unit,
      owner: Identifier
  )

  /** Pending Changes. */
  private val _pending = new mutable.Queue[PendingChange]()

  /**
   * Bindings for foreign components Key = affected component
   */
  private val _foreignBindings = new MutableMultimap[Identifier, ForeignBinding]()

  /** Bindings to Window Events. */
  private val _windowEventBindings = new MutableMultimap[String, WindowEventBinding]()

  /** Bindings to channels. */
  private val _channelBindings = new MutableMultimap[Identifier, ChannelBinding[?]]()

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

  private def activateEvent[E](node: TreeNode, eventBinding: EventBinding[E]): Unit = {
    bindEventSource(node, eventBinding.source, eventBinding.sink)
  }

  def updateModel[T](model: Model[T], updateFn: T => T): Unit = {
    val change = PendingModelChange(model, fn = updateFn)
    _pending.append(change)
    ensureNextIteration()
  }

  def call(callback: () => Unit): Unit = {
    val cb = Callback(callback)
    _pending.append(cb)
    ensureNextIteration()
  }

  private def triggerWeakChannel[T](ref: WeakReference[Channel[T]], data: T): Unit = {
    ref.get match {
      case Some(c) =>
        triggerChannel(c, data)
      case None    => // nothing
    }
  }

  def triggerChannel[T](channel: Channel[T], data: T): Unit = {
    _channelBindings.foreachKey(channel.id) { _.asInstanceOf[ChannelBinding[T]].handler(data) }
  }

  private def bindEventSource[E](ownNode: TreeNode, eventSource: EventSource[E], sink: E => Unit): Unit = {
    eventSource match
      case j: EventSource.Js[_]             =>
        j.jsEvent.componentId match {
          case None              =>
            val handler: Event => Unit = { in =>
              try {
                sink(in)
              } catch {
                case NonFatal(e) =>
                  Logger.warn(s"Exception during window JS Event by component ${ownNode.id}: ${e}")
              }
            }
            _windowEventBindings.add(
              j.jsEvent.name,
              WindowEventBinding(handler, ownNode.id)
            )
            val existing               = _registeredWindowEvents.contains(j.jsEvent.name)
            if (!existing) {
              // The only place to register it, we won't deregister yet
              // TODO: Window event deregistration, KRZ-124
              bindJsEvent(
                org.scalajs.dom.window,
                j.jsEvent.copy(capture = false),
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
      case c: EventSource.ChannelSource[_]  =>
        bindChannel(ownNode, c, sink)
      case o: EventSource.OrSource[_]       =>
        bindEventSource(ownNode, o.left, sink)
        bindEventSource(ownNode, o.right, sink)
      case t: EventSource.Timer             =>
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
      case t: EventSource.Transformer[?, ?] =>
        bindTransformer(ownNode, t, sink)
  }

  private def bindTransformer[E, F](
      ownNode: TreeNode,
      transformer: EventSource.Transformer[E, F],
      sink: F => Unit
  ): Unit = {
    val updatedSink: E => Unit = { input =>
      val transformed = transformer.f(input)
      transformed.foreach(sink)
    }
    bindEventSource(ownNode, transformer.from, updatedSink)
  }

  private def bindChannel[T](ownNode: TreeNode, event: EventSource.ChannelSource[T], sink: T => Unit): Unit = {
    event.channel.get.foreach { c =>
      _channelBindings.add(c.id, ChannelBinding(sink, ownNode.id))
    }
  }

  private def bindJsEvent(
      source: org.scalajs.dom.EventTarget,
      event: JsEvent,
      sink: Event => Unit
  ): Unit = {
    source.addEventListener(
      event.name,
      { (e: Event) =>
        try {
          Logger.debug(s"Reacting to ${event.name} (capture=${event.capture})")
          if (event.preventDefault) {
            e.preventDefault()
          }
          sink(e)
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
      case p: Callback              =>
        p.fn.apply()
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

  private def onWindowEvent(name: String, event: Event): Unit = {
    Logger.debug(s"OnWindowEvent ${name}: Handlers: ${_windowEventBindings.sizeForKey(name)}")
    _windowEventBindings.foreachKey(name) { binding =>
      binding.handler(event)
    }
  }
}
