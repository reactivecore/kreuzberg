package kreuzberg

import kreuzberg.util.MutableMultimap

import scala.collection.mutable
import scala.scalajs.js.timers
import scala.util.control.NonFatal

/** Callback for EventManager. */
trait EventManagerDelegate {

  /** Returns the current state. */
  def state: AssemblyState

  /** Update with a new state */
  def onIterationEnd(
      state: AssemblyState,
      changedModels: Set[ModelId]
  ): Unit

  def locate(componentId: ComponentId): ScalaJsElement
}

/** Encapsulate the highly stateful event handling. */
class EventManager(delegate: EventManagerDelegate) {

  /** A Pending change. */
  private sealed trait PendingChange

  private case class PendingModelChange[M](id: ModelId, fn: M => M) extends PendingChange

  private case class PendingBusCall[M](busId: BusId, value: M) extends PendingChange

  /** There is a next iteration triggered yet */
  private var _hasNextIteration: Boolean = false

  /** We are in a next iteration. */
  private var _inIteration: Boolean = false

  /** During Iteration: Set of changed models. */
  private val _changedModel = mutable.Set[ModelId]()

  /** Bindings to Bus instances. */
  private case class BusBinding[T](
      handler: T => Unit,
      owner: ComponentId
  )

  /** Pending Changes. */
  private val _pending     = new mutable.Queue[PendingChange]()
  private val _busBindings = new MutableMultimap[BusId, BusBinding[_]]()

  private var _currentState = AssemblyState()

  /** Activate Events on a Node. */
  def activateEvents(node: TreeNode): Unit = {
    node.assembly.nodes.foreach { case child: ComponentNode[_] =>
      activateEvents(child)
    }
    node.assembly.bindings.foreach(activateEvent(node, _))
  }
  
  def clear(): Unit = {
    _busBindings.clear()
    _changedModel.clear()
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
    val transformedSink = transformSink(sourceSink.sink)
    bindEventSource(node, sourceSink.source, transformedSink)
  }

  private def transformSink[T](eventSink: EventSink[T]): T => Unit = {
    eventSink match
      case EventSink.ModelChange(model, f) =>
        eventData =>
          val change = PendingModelChange(
            model.id,
            fn = f(eventData, _)
          )
          _pending.append(change)
          ensureNextIteration()
      case EventSink.Custom(f)             => f
      case EventSink.BusCall(bus)          =>
        eventData => {
          println("Adding bus call")
          _pending.append(PendingBusCall(bus.id, eventData))
          ensureNextIteration()
        }
      case EventSink.Multiple(sinks)       =>
        val converted = sinks.map(transformSink)
        eventData => converted.foreach(x => x(eventData))
  }

  private def bindEventSource[E](ownNode: TreeNode, eventSource: EventSource[E], sink: E => Unit): Unit = {
    eventSource match
      case r: EventSource.RepEvent[_, _]              =>
        bindRepEvent(r, sink)
      case o: EventSource.OwnEvent[_]                 =>
        bindOwnEvent(ownNode, o, sink)
      case EventSource.WindowJsEvent(js)              =>
        bindJsEvent(org.scalajs.dom.window, js, sink)
      case EventSource.WithState(inner, from, getter) =>
        bindEventSource(
          ownNode,
          inner,
          x => {
            val mapped = getter.get(delegate.locate(from))
            sink((x, mapped))
          }
        )
      case EventSource.MapSource(from, fn)            =>
        bindEventSource(
          ownNode,
          from,
          x => {
            val mapped = fn(x)
            sink(mapped)
          }
        )
      case EventSource.BusEvent(bus)                  =>
        _busBindings.add(bus.id, BusBinding(sink, ownNode.id))
  }

  private def bindRepEvent[T, E](repEvent: EventSource.RepEvent[T, E], sink: E => Unit): Unit = {
    bindEvent(repEvent.rep, repEvent.event, sink)
  }

  private def bindOwnEvent[E](own: TreeNode, ownEvent: EventSource.OwnEvent[E], sink: E => Unit): Unit = {
    bindEvent(own, ownEvent.event, sink)
  }

  private def bindEvent[T, E](node: TreeNode, event: Event[E], sink: E => Unit): Unit = {
    event match
      case jse: Event.JsEvent              =>
        val source = delegate.locate(node.id)
        bindJsEvent(source, jse, sink)
      case mapped: Event.MappedEvent[_, E] =>
        bindMappedEvent(node, mapped, sink)
  }

  private def bindJsEvent[T, E, M](
      source: org.scalajs.dom.EventTarget,
      event: Event.JsEvent,
      sink: ScalaJsEvent => Unit
  ): Unit = {
    source.addEventListener(
      event.name,
      { (e: ScalaJsEvent) =>
        println(s"Reacting to ${event.name} (capture=${event.capture}, preventDefault=${event.preventDefault})")
        if (event.preventDefault) {
          e.preventDefault()
        }
        sink(e)
      },
      event.capture
    )
  }

  private def bindMappedEvent[E, F](node: TreeNode, mapped: Event.MappedEvent[E, F], sink: F => Unit): Unit = {
    val mappedSink: E => Unit = { in =>
      sink(mapped.mapFn(in))
    }
    bindEvent(node, mapped.underlying, mappedSink)
  }

  private def ensureNextIteration(): Unit = {
    if (!_hasNextIteration && !_inIteration) {
      _hasNextIteration = true
      timers.setTimeout(0) {
        onNextIteration()
      }
    }
  }

  /** Run the next iteration. */
  private def onNextIteration(): Unit = {
    println("Starting Iteration")
    _currentState = delegate.state
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
          println(s"Error in iteration: ${e.getMessage}")
      }
      it += 1
    }
    if (it >= max) {
      println(s"Got more than ${max} iterations, giving up")
    }
    println(s"End Iteration, changed models: ${_changedModel}")
    _inIteration = false
    delegate.onIterationEnd(_currentState, _changedModel.toSet)
  }

  private def handlePendingChange(change: PendingChange): Unit = {
    change match {
      case p: PendingModelChange[_] =>
        handlePendingModelChange(p)
      case p: PendingBusCall[_]     =>
        handlePendingBusCall(p)
    }
  }

  private def handlePendingModelChange[T](change: PendingModelChange[T]): Unit = {
    val value   = _currentState.modelValues(change.id).asInstanceOf[T]
    val updated = change.fn(value)
    _currentState = _currentState.withModelValue(change.id, updated)
    _changedModel.add(change.id)
  }

  private def handlePendingBusCall[T](change: PendingBusCall[T]): Unit = {
    println("Handling bus call")
    _busBindings.foreachKey(change.busId) { binding =>
      println("Handling bus call (inner loop")
      binding.asInstanceOf[BusBinding[T]].handler(change.value)
    }
  }
}
