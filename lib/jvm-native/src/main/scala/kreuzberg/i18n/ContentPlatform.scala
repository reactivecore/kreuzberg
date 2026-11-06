package kreuzberg.i18n

import kreuzberg.SimpleThreadLocal

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Outside the browser, content is provided by a [[ContentPlatform.Backend]] per thread, e.g. by a server-side engine.
 * Without one, there is no content.
 */
private[kreuzberg] object ContentPlatform {

  /** Provides rendered content in the locale of the current page. */
  trait Backend {

    /** Returns `None` for unknown documents. */
    def load(name: String): Option[String]
  }

  /** No content. */
  object NoBackend extends Backend {
    override def load(name: String): Option[String] = None
  }

  private val backend = new SimpleThreadLocal[Backend](NoBackend)

  /** Run `f` with a backend for the current thread. */
  def withBackend[T](b: Backend)(f: => T): T = backend.withInstance(b)(f)

  /** Already completed; errors of the backend (e.g. rendering) are returned as failure. */
  def load(name: String): Future[String] = {
    Future.fromTry(Try(backend.get().load(name)).flatMap {
      case Some(html) => Success(html)
      case None       => Failure(new NoSuchElementException(s"Unknown content: ${name}"))
    })
  }
}
