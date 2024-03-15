package kreuzberg.extras

object BrowserRouting {

  def getCurrentResource(): UrlResource = {
    UrlResource("")
  }
  
  def setDocumentTitle(title: String): Unit = {
    // empty
  }

  def pushState(title: String, target: String): Unit = {
    // Empty
  }

  def replaceState(title: String, target: String): Unit = {
    // Empty
  }
}
