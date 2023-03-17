package kreuzberg.engine.naive

import kreuzberg.*
import kreuzberg.engine.naive.utils.MutableMultimap
import kreuzberg.dom.*

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Encapsulate the highly stateful event handling. */
class EventManager(delegate: EventManagerDelegate) {

  /** A Pending change. */
  private sealed trait PendingChange

  private case class PendingModelChange[M](id: ModelId, fn: M => M) extends PendingChange

  /** There is a next iteration triggered yet */
  private var _hasNextIteration: Boolean = false

  /** We are in a next iteration. */
  private var _inIteration: Boolean = false

  /** During Iteration: Set of changed models. */
  private val _changedModel = mutable.Set[ModelId]()

  /** Bindings to Model instances. */
  private case class ModelBindings[T](
      handler: (T, T) => Unit,
      owner: ComponentId
  )

  /**
   * Bindings to Window-Events. We cannot directly move them to the inner handler, as we need a simple way to deconnect
   * garbage collected / dereferenced components from it.
   */
  private case class WindowEventBinding(
      handler: ScalaJsEvent => Unit,
      owner: ComponentId,
      preventDefault: Boolean
  )

  /** Binding to Component Events. */
  private case class ComponentEventBinding[T](
      handler: T => Unit,
      owner: ComponentId
  )

  /** Pending Changes. */
  private val _pending = new mutable.Queue[PendingChange]()

  /** Bindings to Model changes. */
  private val _modelBindings = new MutableMultimap[ModelId, ModelBindings[_]]()

  /** Bindings to Window Events. */
  private val _windowEventBindings = new MutableMultimap[String, WindowEventBinding]()

  /** Bindings to Component Events. */
  private val _componentEventBindings = new MutableMultimap[(ComponentId, String), ComponentEventBinding[_]]()

  // Keeping track of registered window events (we do not yet remove them)
  private val _registeredWindowEvents = mutable.Set[String]()

  private var _currentState = AssemblyState()

  /** Activate Events on a Node. */
  def activateEvents(node: TreeNode): Unit = {
    // Drop Existing model bindings
    _modelBindings.filterValuesInPlace(_.owner != node.id)
    _windowEventBindings.filterValuesInPlace(_.owner != node.id)
    _componentEventBindings.filterValuesInPlace(_.owner != node.id)

    node.assembly.nodes.foreach { case child: ComponentNode[_, _] =>
      activateEvents(child)
    }
    node.assembly.bindings.foreach(activateEvent(node, _))
  }

  def clear(): Unit = {
    Logger.debug("Full Clear")
    _modelBindings.clear()
    _changedModel.clear()
  }

  def garbageCollect(referencedComponents: Set[ComponentId], referencedModels: Set[ModelId]): Unit = {
    _modelBindings.filterKeysInPlace(referencedModels.contains)
    _modelBindings.filterValuesInPlace(binding => referencedComponents.contains(binding.owner))
    _windowEventBindings.filterValuesInPlace(binding => referencedComponents.contains(binding.owner))
    _componentEventBindings.filterValuesInPlace(binding => referencedComponents.contains(binding.owner))
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
      case EventSink.ModelChange(model, f)    =>
        eventData =>
          val change = PendingModelChange(
            model.id,
            fn = f(eventData, _)
          )
          _pending.append(change)
          ensureNextIteration()
      case EventSink.ExecuteCode(f)           => f
      case EventSink.Multiple(sinks)          =>
        val converted = sinks.map(transformSink(node, _))
        eventData => converted.foreach(x => x(eventData))
      case EventSink.CustomEventSink(dst, ce) =>
        eventData =>
          _componentEventBindings.foreachKey(dst.getOrElse(node.id) -> ce.name) { binding =>
            binding.asInstanceOf[ComponentEventBinding[T]].handler(eventData)
          }
  }

  private def buildRuntimeContext(id: ComponentId): RuntimeContext = {
    new RuntimeContext {
      def jsElement: ScalaJsElement = delegate.locate(id) // TODO: Wir brauchen eine unsafeDelegate was die Dinger sofort heraussucht.

      def jump(componentId: ComponentId): RuntimeContext = buildRuntimeContext(componentId)
    }
  }

  private def bindEventSource[E](ownNode: TreeNode, eventSource: EventSource[E], sink: E => Unit): Unit = {
    eventSource match
      case r: EventSource.ComponentEvent[_]                     =>
        bindRepEvent(ownNode, r, sink)
      case EventSource.WindowJsEvent(js)                        =>
        _windowEventBindings.add(
          js.name,
          WindowEventBinding(sink, ownNode.id, js.preventDefault)
        )
        val existing = _registeredWindowEvents.contains(js.name)
        if (!existing) {
          // The only place to register it, we won't deregister yet
          // TODO: Window event deregistration, KRZ-124
          bindJsEvent(org.scalajs.dom.window, js.copy(capture = false), event => onWindowEvent(js.name, event))
          _registeredWindowEvents.add(js.name)
          Logger.debug(s"Fresh bound ${js.name} on window")
        }
      case EventSource.WithState(inner, componentId, provider, decoder) =>
        bindEventSource(
          ownNode,
          inner,
          x => {
            val context = buildRuntimeContext(componentId)
            val mapped = decoder(provider(context))
            sink((x, mapped))
          }
        )
      case EventSource.MapSource(from, fn)                      =>
        bindEventSource(
          ownNode,
          from,
          x => {
            val mapped = fn(x)
            sink(mapped)
          }
        )
      case e: EventSource.EffectEvent[_, _, _]                  =>
        bindEffect(ownNode, e, sink)
      case m: EventSource.ModelChange[_]                        =>
        bindModelChange(
          ownNode,
          m,
          (from, to) => {
            sink(from, to)
          }
        )
      case a: EventSource.AndSource[_]                          =>
        bindAnd(ownNode, a, sink)
  }

  private def bindModelChange[T](ownNode: TreeNode, source: EventSource.ModelChange[T], sink: (T, T) => Unit): Unit = {
    _modelBindings.add(source.model.id, ModelBindings(sink, ownNode.id))
  }

  private def bindRepEvent[E](ownNode: TreeNode, repEvent: EventSource.ComponentEvent[E], sink: E => Unit): Unit = {
    val id = repEvent.component.getOrElse(ownNode.id)
    bindEvent(id, repEvent.event, sink)
  }

  private def bindEffect[E, F[_], R](
      own: TreeNode,
      event: EventSource.EffectEvent[E, F, R],
      sink: Try[R] => Unit
  ): Unit = {
    val decoder: F[R] => Future[R] = event.effectOperation.support.name match {
      case EffectSupport.FutureName => in => in.asInstanceOf[Future[R]]
      case unknown                  =>
        throw new NotImplementedError(s"Unsupported effect type ${unknown}")
    }
    bindEventSource(
      own,
      event.trigger,
      v =>
        decoder(event.effectOperation.fn(v)).andThen { case result =>
          sink(result)
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

  private def bindEvent[E](componentId: ComponentId, event: Event[E], sink: E => Unit): Unit = {
    event match
      case jse: Event.JsEvent              =>
        val source = delegate.locate(componentId)
        bindJsEvent(source, jse, sink)
      case ce: Event.Custom[E]             =>
        val handler = ComponentEventBinding(sink, componentId)
        _componentEventBindings.add(componentId -> ce.name, handler)
      case mapped: Event.MappedEvent[_, E] =>
        bindMappedEvent(componentId, mapped, sink)
      case Event.Assembled                 =>
        scalajs.js.timers.setTimeout(0) {
          sink(())
        }
  }

  private def bindJsEvent[T, E, M](
      source: org.scalajs.dom.EventTarget,
      event: Event.JsEvent,
      sink: ScalaJsEvent => Unit
  ): Unit = {
    source.addEventListener(
      event.name,
      { (e: ScalaJsEvent) =>
        Logger.debug(s"Reacting to ${event.name} (capture=${event.capture}, preventDefault=${event.preventDefault})")
        if (event.preventDefault) {
          e.preventDefault()
        }
        sink(e)
      },
      event.capture
    )
  }

  private def bindMappedEvent[E, F](
      componentId: ComponentId,
      mapped: Event.MappedEvent[E, F],
      sink: F => Unit
  ): Unit = {
    val mappedSink: E => Unit = { in =>
      sink(mapped.mapFn(in))
    }
    bindEvent(componentId, mapped.underlying, mappedSink)
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
    val value   = _currentState.modelValues(change.id).asInstanceOf[T]
    val updated = change.fn(value)
    if (value != updated) {
      Logger.debug(
        s"Updating ${change.id} from ${value} to ${updated}, bindings: ${_modelBindings.sizeForKey(change.id)}"
      )
      _currentState = _currentState.withModelValue(change.id, updated)
      _changedModel.add(change.id)
      _modelBindings.foreachKey(change.id) { binding =>
        binding.asInstanceOf[ModelBindings[T]].handler(value, updated)
      }
    }
  }

  private def onWindowEvent(name: String, event: ScalaJsEvent): Unit = {
    Logger.debug(s"OnWindowEvent ${name}: Handlers: ${_windowEventBindings.sizeForKey(name)}")
    _windowEventBindings.foreachKey(name) { binding =>
      if (binding.preventDefault) {
        event.preventDefault()
      }
      binding.handler(event)
    }
  }
}
