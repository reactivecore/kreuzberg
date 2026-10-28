package kreuzberg.extras

/** Backend for browser routing operations, allowing platform-specific or server-side implementations. */
trait BrowserRoutingBackend {
  def getCurrentResource(): UrlResource
  def setDocumentTitle(title: String): Unit
  def pushState(title: String, target: String): Unit
  def replaceState(title: String, target: String): Unit
}
