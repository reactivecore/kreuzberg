package kreuzberg.engine.server

import java.util.UUID
import scala.collection.mutable

/**
 * One page load in the browser, with its outbox of operations.
 *
 * Operations are numbered and delivered in order by the page's stream (see [[EventController]]), so they are never
 * reordered. Operations are kept until the client confirms them (with each event, or when reconnecting), so that a
 * reconnecting stream can resume where it left off. At most [[Page.MaxBufferedChars]] are kept; if a client fell
 * further behind, the page has to be reloaded.
 */
private[server] class Page(val id: String = UUID.randomUUID().toString) {
  private val buffer   = mutable.ArrayDeque[String]()
  private var chars    = 0L // scalafix:ok
  private var firstSeq = 1L // scalafix:ok
  private var nextSeq  = 1L // scalafix:ok

  /** Append operations, numbering them. */
  def append(ops: Seq[ClientProtocol.Op]): Unit = synchronized {
    ops.foreach { op =>
      val line = ClientProtocol.line(op, nextSeq)
      buffer.append(line)
      chars += line.length
      nextSeq += 1
    }
    while (chars > Page.MaxBufferedChars && buffer.nonEmpty) dropFirst()
  }

  /** The client received all operations up to `seq`, they are not needed anymore. */
  def confirm(seq: Long): Unit = synchronized {
    while (firstSeq <= seq && buffer.nonEmpty) dropFirst()
  }

  private def dropFirst(): Unit = {
    chars -= buffer.removeHead().length
    firstSeq += 1
  }

  /** Number of the last appended operation (0 if none). */
  def lastSeq: Long = synchronized(nextSeq - 1)

  def hasAfter(seq: Long): Boolean = synchronized(seq < nextSeq - 1)

  /**
   * Encoded operations after `seq`.
   * @return
   *   None, if some of them are not buffered anymore, or `seq` is unknown.
   */
  def after(seq: Long): Option[Seq[String]] = synchronized {
    if (seq + 1 < firstSeq || seq >= nextSeq) None
    else Some(buffer.drop((seq + 1 - firstSeq).toInt).toSeq)
  }
}

private[server] object Page {

  /** Size of unconfirmed operations kept for reconnecting streams. */
  val MaxBufferedChars: Long = 4L * 1024L * 1024L
}
