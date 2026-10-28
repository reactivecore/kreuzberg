package kreuzberg.extras

import kreuzberg.SimpleThreadLocal

object BrowserRouting {

  private object NoOpBackend extends BrowserRoutingBackend {
    def getCurrentResource(): UrlResource                 = UrlResource("")
    def setDocumentTitle(title: String): Unit             = {}
    def pushState(title: String, target: String): Unit    = {}
    def replaceState(title: String, target: String): Unit = {}
  }

  private val _backend = new SimpleThreadLocal[BrowserRoutingBackend](NoOpBackend)

  /** Execute a block with a specific routing backend. */
  def withBackend[T](backend: BrowserRoutingBackend)(f: => T): T = {
    _backend.withInstance(backend)(f)
  }

  def getCurrentResource(): UrlResource = {
    _backend.get().getCurrentResource()
  }

  def setDocumentTitle(title: String): Unit = {
    _backend.get().setDocumentTitle(title)
  }

  def pushState(title: String, target: String): Unit = {
    _backend.get().pushState(title, target)
  }

  def replaceState(title: String, target: String): Unit = {
    _backend.get().replaceState(title, target)
  }
}
