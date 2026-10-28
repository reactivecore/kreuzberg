package kreuzberg.engine.server

import kreuzberg.extras.{BrowserRoutingBackend, UrlResource}

/** Routing backend of a session; history changes are sent to the browser after the event is processed. */
private[server] class ServerBrowserRoutingBackend(session: Session) extends BrowserRoutingBackend {

  override def getCurrentResource(): UrlResource = session.currentUrl

  override def setDocumentTitle(title: String): Unit = {
    session.currentTitle = title
  }

  override def pushState(title: String, target: String): Unit = changeUrl(title, target, push = true)

  override def replaceState(title: String, target: String): Unit = changeUrl(title, target, push = false)

  private def changeUrl(title: String, target: String, push: Boolean): Unit = {
    session.currentUrl = UrlResource(target)
    session.currentTitle = title
    session.pendingUrlUpdates :+= Session.UrlUpdate(title, target, push)
  }
}
