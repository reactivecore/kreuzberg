package kreuzberg.extras

object BrowserRouting {
  def getCurrentResource(): UrlResource = {
    val pathname = org.scalajs.dom.window.location.pathname
    val search   = org.scalajs.dom.window.location.search
    val fragment = org.scalajs.dom.window.location.hash
    UrlResource(pathname + search + fragment)
  }

  def setDocumentTitle(title: String): Unit = {
    org.scalajs.dom.document.title = title
  }

  def pushState(title: String, target: String): Unit = {
    org.scalajs.dom.window.history.pushState((), title, target)
  }

  def replaceState(title: String, target: String): Unit = {
    org.scalajs.dom.window.history.replaceState((), title, target)
  }
}
