package kreuzberg.engine.server

import kreuzberg.*
import kreuzberg.extras.UrlResource
import kreuzberg.i18n.Translations
import org.slf4j.LoggerFactory

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*

/** Holds the sessions, removing expired ones periodically. */
private[server] class SessionManager(
    component: => Component,
    serviceRepositoryFactory: () => ServiceRepository,
    expiry: FiniteDuration
) extends AutoCloseable {
  private val logger   = LoggerFactory.getLogger(getClass)
  private val sessions = ConcurrentHashMap[String, Session]()

  /** Removes expired sessions and runs the timers of all sessions (a tick only queues work). */
  private val scheduler = Executors.newSingleThreadScheduledExecutor { r =>
    val t = Thread(r, "kreuzberg-sessions")
    t.setDaemon(true)
    t
  }

  private val cleanupInterval = expiry.toMillis.max(1000L)

  scheduler.scheduleAtFixedRate(() => cleanup(), cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS)

  /** Returns a non-expired session. */
  def get(sessionId: String): Option[Session] = {
    Option(sessions.get(sessionId)).filterNot(_.isExpired(expiry.toMillis))
  }

  /**
   * Open a page: reuse the session if it is still valid, otherwise create a new one. The component tree is assembled
   * for the given URL and translations and settled.
   *
   * @param render
   *   called with the settled session, under its lock.
   * @return
   *   the result of render and true, if a new session was created.
   */
  def openPage[T](sessionId: Option[String], url: UrlResource, translations: Translations = Translations.empty)(
      render: Session => T
  ): (T, Boolean) = {
    val existing = sessionId.flatMap(get)
    val session  = existing.getOrElse(createSession())
    val result   = session.withLock {
      session.page = Page()
      // Streams of the previous page end
      session.signalActivity()
      val previousUrl = session.currentUrl
      session.currentUrl = url
      session.translations = translations
      session.pendingLocale = None
      // A fresh page has fresh inputs
      session.inputs = Map.empty
      session.pendingWrites = Vector.empty
      session.assemble(component)
      if (existing.nonEmpty && previousUrl != url) {
        // Like history navigation: components (e.g. the router) keep their state in the session's models, so they
        // have to learn about the new URL.
        session.firePopState()
      }
      session.settle(forPage = true)()
      session.titleShown = session.currentTitle
      render(session)
    }
    (result, existing.isEmpty)
  }

  def sessionCount: Int = sessions.size()

  /** Remove expired sessions. */
  def cleanup(): Unit = {
    val expired = sessions.asScala.collect { case (id, s) if s.isExpired(expiry.toMillis) => id }.toList
    expired.foreach { id => Option(sessions.remove(id)).foreach(_.close()) }
    if (expired.nonEmpty) {
      logger.debug(s"Removed ${expired.size} expired sessions, ${sessions.size()} remaining")
    }
  }

  override def close(): Unit = {
    scheduler.shutdownNow()
    sessions.clear()
  }

  private def createSession(): Session = {
    val id      = UUID.randomUUID().toString
    val session = Session(id, serviceRepositoryFactory(), scheduler)
    sessions.put(id, session)
    logger.debug(s"Created session ${id}")
    session
  }
}
