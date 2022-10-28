package kreuzberg

import kreuzberg.util.{MutableMultimap, Stateful}
import org.scalajs.dom.{Element, document, Event as JsEvent}

import scala.collection.mutable
import scala.scalajs.js.timers
import scala.util.control.NonFatal

class Binder[T](rootElement: Element, main: T)(implicit assembler: Assembler[T]) {

  sealed trait PendingChange

  case class PendingModelChange[M](id: ModelId, fn: M => M) extends PendingChange
  case class PendingBusCall[M](busId: BusId, value: M)      extends PendingChange

  case class BusBinding[T](
      handler: T => Unit,
      owner: ComponentId
  )

  private var _currentState: AssemblyState = AssemblyState()
  private var _tree: Option[Node]          = None
  private var _hasNextIteration: Boolean   = false
  private var _inIteration: Boolean        = false

  private val _busBindings  = new MutableMultimap[BusId, BusBinding[_]]()
  private val _pending      = new mutable.Queue[PendingChange]()
  // Changed Models during an event iteration
  private val _changedModel = mutable.Set[ModelId]()

  private val viewer = new Viewer(rootElement)

  def run(): Unit = {
    redraw()
  }

  private def redraw(): Unit = {
    println("Starting Redraw")
    val (nextState, tree) = assembler.assembleNamedChild("root", main)(_currentState)
    viewer.drawRoot(tree)
    _currentState = nextState
    _tree = Some(tree)

    _busBindings.clear()
    activateEvents(tree)
    _currentState = garbageCollect(tree, _currentState)
    println("End Redraw")
  }

  private def redrawChanged(): Unit = {
    val changedModels     = _changedModel.toSet
    val changedContainers = (_currentState.subscribers.collect {
      case (modelId, containerId) if changedModels.contains(modelId) => containerId
    }).toSet

    println(s"Changed Containers: ${changedContainers}")
    if (changedContainers.isEmpty) {
      println("No changed containers, skipping redraw")
      return
    }
    // Das ist garnicht so einfach, weil der Baum im ganzen nicht so einfach mutiert werden kann und wir
    // Ja dann noch die vielen blöden Events neu zeichnen müssten ?!

    val (updatedState, updatedTree) = updateTree(_tree.get, changedContainers)(_currentState)
    _tree = Some(updatedTree)
    drawChangedEvents(updatedTree, changedContainers)
    activateChangedEvents(updatedTree, changedContainers)
    _currentState = garbageCollect(updatedTree, updatedState)
  }

  private def updateTree(node: Node, changed: Set[ComponentId]): Stateful[AssemblyState, Node] = {
    if (changed.contains(node.id)) {
      reassembleNode(node)
    } else {
      transformNodeChildren(node, updateTree(_, changed))
    }
  }

  private def reassembleNode(node: Node): Stateful[AssemblyState, Node] = {
    node match {
      case r: Rep[_] =>
        r.assembler.assembleWithId(node.id, r.value)
    }
  }

  private def transformNodeChildren(
      node: Node,
      f: Node => Stateful[AssemblyState, Node]
  ): Stateful[AssemblyState, Node] = {
    node match {
      case r: Rep[_] =>
        r.assembly match {
          case p: Assembly.Pure      => Stateful.pure(node)
          case c: Assembly.Container =>
            for {
              updated <- Stateful.accumulate(c.nodes)(f)
            } yield {
              val updatedAssembly = c.copy(
                nodes = updated
              )
              r.copy(assembly = updatedAssembly)
            }
        }
    }
  }

  private def drawChangedEvents(node: Node, changed: Set[ComponentId]): Unit = {
    if (changed.contains(node.id)) {
      viewer.updateNode(node)
    } else {
      node.children.foreach(drawChangedEvents(_, changed))
    }
  }

  private def activateChangedEvents(node: Node, changed: Set[ComponentId]): Unit = {
    if (changed.contains(node.id)) {
      activateEvents(node)
    } else {
      node.children.foreach(activateChangedEvents(_, changed))
    }
  }

  def onNextIteration(): Unit = {
    println("Starting Iteration")
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
    redrawChanged()
  }

  private def garbageCollect(tree: Node, state: AssemblyState): AssemblyState = {
    val referencedComponents = collectReferencedComponents(tree)

    val componentFiltered = state.copy(
      children = state.children.filterKeys(referencedComponents.contains),
      models = state.models.filterKeys(referencedComponents.contains),
      busses = state.busses.filterKeys(referencedComponents.contains)
    )

    val referencedModels = componentFiltered.models.values.map(_.id).toSet
    val modelFiltered    = componentFiltered.copy(
      modelValues = componentFiltered.modelValues.view.filterKeys(referencedModels.contains).toMap,
      subscribers = componentFiltered.subscribers.filter { case (modelId, componentId) =>
        referencedModels.contains(modelId) && referencedComponents.contains(componentId)
      }.distinct
    )

    println(s"Garbage Collecting Referenced: ${referencedComponents.size} Components/ ${referencedModels.size} Models")
    println(s"  Children: ${state.children.size} -> ${modelFiltered.children.size}")
    println(s"  Models:   ${state.models.size}   -> ${modelFiltered.models.size}")
    println(s"  Busses:   ${state.busses.size}   -> ${modelFiltered.busses.size}")
    println(s"  Values:   ${state.modelValues.size} -> ${modelFiltered.modelValues.size}")
    println(s"  Subscribers: ${state.subscribers.size} -> ${modelFiltered.subscribers.size}")

    modelFiltered
  }

  private def collectReferencedComponents(tree: Node): Set[ComponentId] = {
    val builder                   = Set.newBuilder[ComponentId]
    def iterate(node: Node): Unit = {
      builder += node.id
      node.children.foreach(iterate)
    }
    iterate(tree)
    builder.result()
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

  def ensureNextIteration(): Unit = {
    if (!_hasNextIteration && !_inIteration) {
      _hasNextIteration = true
      timers.setTimeout(0) {
        onNextIteration()
      }
    }
  }

  def activateEvents(node: Node): Unit = {
    node.assembly.nodes.foreach { case child: Rep[_] =>
      activateEvents(child)
    }
    node.assembly.bindings.foreach(activateEvent(node, _))
  }

  def activateEvent(node: Node, eventBinding: EventBinding): Unit = {
    eventBinding match {
      case s: EventBinding.SourceSink[_] => activateSourceSinkBinding(node, s)
    }
  }

  def activateSourceSinkBinding[E](
      node: Node,
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

  private def bindEventSource[E](ownNode: Node, eventSource: EventSource[E], sink: E => Unit): Unit = {
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
            val mapped = getter.get(from, viewer)
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

  private def bindOwnEvent[E](own: Node, ownEvent: EventSource.OwnEvent[E], sink: E => Unit): Unit = {
    bindEvent(own, ownEvent.event, sink)
  }

  private def bindEvent[T, E](node: Node, event: Event[E], sink: E => Unit): Unit = {
    event match
      case jse: Event.JsEvent              =>
        val source = viewer.findElement(node.id)
        bindJsEvent(source, jse, sink)
      case mapped: Event.MappedEvent[_, E] =>
        bindMappedEvent(node, mapped, sink)
  }

  private def bindJsEvent[T, E, M](
      source: org.scalajs.dom.EventTarget,
      event: Event.JsEvent,
      sink: JsEvent => Unit
  ): Unit = {
    source.addEventListener(
      event.name,
      { (e: JsEvent) =>
        println(s"Reacting to ${event.name} (capture=${event.capture}, preventDefault=${event.preventDefault})")
        if (event.preventDefault) {
          e.preventDefault()
        }
        sink(e)
      },
      event.capture
    )
  }

  private def bindMappedEvent[E, F](node: Node, mapped: Event.MappedEvent[E, F], sink: F => Unit): Unit = {
    val mappedSink: E => Unit = { in =>
      sink(mapped.mapFn(in))
    }
    bindEvent(node, mapped.underlying, mappedSink)
  }
}
