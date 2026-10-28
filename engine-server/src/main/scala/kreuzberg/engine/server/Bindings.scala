package kreuzberg.engine.server

import kreuzberg.*
import org.scalajs.dom.Event
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

/**
 * Event handlers of a component tree, as seen from the server.
 *
 * Mirrors the naive engine's event activation: transformers are applied to the sinks and every handler is wrapped so
 * that a failing handler does not break the others.
 *
 * @param listeners
 *   DOM event listeners the browser has to forward to the server.
 * @param timers
 *   handlers of timers the browser has to run.
 * @param channels
 *   channel handlers, keyed by channel id.
 */
private[server] case class Bindings(
    listeners: Map[Bindings.ListenerKey, Bindings.Listener] = Map.empty,
    timers: Map[Bindings.TimerKey, Vector[() => Unit]] = Map.empty,
    channels: Map[Identifier, Vector[Any => Unit]] = Map.empty
)

private[server] object Bindings {

  /** Where a DOM listener is installed. */
  enum ListenerKey {

    /** Listener on the element of component `target`. */
    case Element(target: Identifier, eventName: String)

    /** Listener on `window`, installed via the element of component `owner`. */
    case Window(owner: Identifier, eventName: String)

    def eventName: String
  }

  /** A DOM listener, merging all handlers bound to the same [[ListenerKey]]. */
  case class Listener(
      handlers: Vector[Event => Unit],
      preventDefault: Boolean,
      capture: Boolean
  ) {
    def merge(other: Listener): Listener = Listener(
      handlers ++ other.handlers,
      preventDefault || other.preventDefault,
      capture || other.capture
    )
  }

  /** Timers of one owner with the same settings share one key. */
  case class TimerKey(owner: Identifier, periodic: Boolean, duration: FiniteDuration)

  private val logger = LoggerFactory.getLogger(getClass)

  /** Collect all bindings of a tree. */
  def collect(tree: TreeNode): Bindings = {
    val builder = Builder()
    tree.iterator.foreach { node =>
      node.handlers.foreach { binding => builder.add(node.id, binding.source, binding.sink) }
    }
    builder.result()
  }

  private class Builder {
    private val listeners = mutable.LinkedHashMap[ListenerKey, Listener]()
    private val timers    = mutable.LinkedHashMap[TimerKey, Vector[() => Unit]]()
    private val channels  = mutable.LinkedHashMap[Identifier, Vector[Any => Unit]]()

    def add[E](owner: Identifier, source: EventSource[E], sink: E => Unit): Unit = {
      source match {
        case EventSource.Js(jsEvent)          =>
          val key      = jsEvent.componentId match {
            case Some(target) => ListenerKey.Element(target, jsEvent.name)
            case None         => ListenerKey.Window(owner, jsEvent.name)
          }
          val listener = Listener(Vector(guarded(owner, sink)), jsEvent.preventDefault, jsEvent.capture)
          listeners.updateWith(key) {
            case Some(existing) => Some(existing.merge(listener))
            case None           => Some(listener)
          }
        case c: EventSource.ChannelSource[?]  =>
          c.channel.get.foreach { channel =>
            channels.updateWith(channel.id) { existing =>
              Some(existing.getOrElse(Vector.empty) :+ guarded(owner, sink).asInstanceOf[Any => Unit])
            }
          }
        case t: EventSource.Timer             =>
          val handler = guarded(owner, sink)
          timers.updateWith(TimerKey(owner, t.periodic, t.duration)) { existing =>
            Some(existing.getOrElse(Vector.empty) :+ (() => handler(())))
          }
        case o: EventSource.OrSource[?]       =>
          add(owner, o.left, sink)
          add(owner, o.right, sink)
        case t: EventSource.Transformer[?, ?] =>
          addTransformer(owner, t, sink)
      }
    }

    private def addTransformer[A, E](owner: Identifier, t: EventSource.Transformer[A, E], sink: E => Unit): Unit = {
      add(owner, t.from, (a: A) => t.f(a).foreach(sink))
    }

    def result(): Bindings = Bindings(listeners.toMap, timers.toMap, channels.toMap)
  }

  private def guarded[E](owner: Identifier, sink: E => Unit): E => Unit = { value =>
    try {
      sink(value)
    } catch {
      case NonFatal(e) => logger.warn(s"Exception in event handler of component ${owner}", e)
    }
  }
}
