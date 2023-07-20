package kreuzberg.extras

object BrowserRouting {
  def getCurrentPath(): String = {
    org.scalajs.dom.window.location.pathname
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
