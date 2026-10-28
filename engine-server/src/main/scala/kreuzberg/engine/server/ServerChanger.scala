package kreuzberg.engine.server

import kreuzberg.*
import kreuzberg.engine.ModelValues
import org.scalajs.dom.Element
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.control.NonFatal

/**
 * Server-side [[Changer]], queueing model changes until [[processPending]] is called.
 *
 * Enqueueing is thread safe, as async callbacks may run on other threads. Everything else must be called under the
 * session lock.
 */
private[server] class ServerChanger(session: Session) extends Changer {
  import ServerChanger.*

  private val queueLock = Object()
  private val queue     = mutable.Queue[PendingChange]()

  /** State during [[processPending]], so that [[eagerState]] sees changes of the running iteration. */
  private var iterationState: Option[ModelValues] = None // scalafix:ok

  override def updateModel[T](model: Model[T], updateFn: T => T): Unit = enqueue(PendingModelChange(model, updateFn))

  override def call(callback: () => Unit): Unit = enqueue(PendingCallback(callback))

  override def eagerState[T](model: Model[T]): T = iterationState.getOrElse(session.modelValues).value(model)

  override def triggerChannel[T](channel: Channel[T], value: T): Unit = {
    session.bindings.channels.get(channel.id).foreach(_.foreach(_(value)))
  }

  override def locate(identifier: Identifier): Element = {
    val tag = session.tree.iterator.find(_.id == identifier).flatMap(n => MockElement.rootTag(n.html))
    MockElement(session, identifier, tag)
  }

  private def enqueue(change: PendingChange): Unit = {
    queueLock.synchronized(queue.enqueue(change))
    session.signalActivity()
  }

  def hasPending: Boolean = queueLock.synchronized(queue.nonEmpty)

  private def dequeue(): Option[PendingChange] = queueLock.synchronized {
    Option.when(queue.nonEmpty)(queue.dequeue())
  }

  /** Process all pending changes and return the IDs of changed models. */
  def processPending(): Set[Identifier] = {
    var state   = session.modelValues // scalafix:ok
    val changed = mutable.Set[Identifier]()

    def applyChange[T](change: PendingModelChange[T]): Unit = {
      val value   = state.value(change.model)
      val updated = change.fn(value)
      if (value != updated) {
        state = state.withModelValue(change.model.id, updated)
        changed += change.model.id
      }
    }

    @tailrec
    def loop(iterations: Int): Unit = {
      if (iterations >= MaxIterations) {
        logger.warn(s"More than ${MaxIterations} pending changes in session ${session.id}, continuing later")
      } else {
        dequeue() match {
          case None         => ()
          case Some(change) =>
            try {
              change match {
                case p: PendingModelChange[?] => applyChange(p)
                case p: PendingCallback       => p.fn()
              }
            } catch {
              case NonFatal(e) => logger.warn("Error while processing a pending change", e)
            }
            iterationState = Some(state)
            loop(iterations + 1)
        }
      }
    }

    iterationState = Some(state)
    try {
      loop(0)
    } finally {
      iterationState = None
    }
    session.modelValues = state
    changed.toSet
  }
}

private[server] object ServerChanger {
  private val logger = LoggerFactory.getLogger(getClass)

  private val MaxIterations = 10000

  private sealed trait PendingChange

  private case class PendingModelChange[M](model: Model[M], fn: M => M) extends PendingChange

  private case class PendingCallback(fn: () => Unit) extends PendingChange
}
