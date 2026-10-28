package kreuzberg.engine.server

import kreuzberg.*
import kreuzberg.engine.UpdatePath
import kreuzberg.miniserver.DeploymentConfig
import _root_.scalatags.Text.all.*
import _root_.scalatags.Text.tags2.title as titleTag

/** Renders pages and tree changes for `kreuzberg-client.js`. */
private[server] object ClientRenderer {

  /**
   * Render a whole page.
   *
   * @param ops
   *   operations the client applies on start (bindings first)
   */
  def renderPage(
      session: Session,
      deploymentConfig: DeploymentConfig,
      clientUrl: String,
      ops: Seq[ClientProtocol.Op],
      lang: Option[String]
  ): String = {
    val init = ClientProtocol.Op.bindings(session.bindings) +: ops
    val page = html(lang.map(l => attr("lang") := l), deploymentConfig.htmlRootAttributes)(
      head(
        deploymentConfig.extraHtmlHeader.map(h => RawFrag(h.renderToString())),
        Option.when(session.currentTitle.nonEmpty)(titleTag(session.currentTitle)),
        script(attr("defer").empty, src := clientUrl),
        deploymentConfig.extraJs.map { name => script(src := deploymentConfig.hashedUrl(name)) },
        deploymentConfig.extraCss.map { name => link(rel := "stylesheet", href := deploymentConfig.hashedUrl(name)) }
      ),
      body(
        div(id        := "root", attr("data-page") := session.page.id, RawFrag(session.tree.render())),
        script(`type` := "application/json", id    := "krz-init", RawFrag(ClientProtocol.initScript(init)))
      )
    )
    "<!DOCTYPE html>\n" + page.toString
  }

  /** Operations of one processing step. */
  def renderStep(session: Session, changes: Seq[UpdatePath.Change]): Seq[ClientProtocol.Op] = {
    val elements   = changes.collect {
      case UpdatePath.Change.Rerender(node)           => ClientProtocol.Op.Replace(node.id, node.render())
      // The HTML of the assembly, embedding the new components
      case UpdatePath.Change.AppendHtml(id, _, html)  => ClientProtocol.Op.Append(id, html)
      case UpdatePath.Change.PrependHtml(id, _, html) => ClientProtocol.Op.Prepend(id, html)
      // Headless components have no HTML
    }
    // After changes of the tree, as listeners may refer to new elements
    val bindings   = Option.when(changes.nonEmpty)(ClientProtocol.Op.bindings(session.bindings))
    val writes     = session.takePendingWrites().map(w => ClientProtocol.Op.Set(w.id, w.property, w.value))
    val urlUpdates = session.takePendingUrlUpdates()
    val history    = urlUpdates.map(u => ClientProtocol.Op.History(u.title, u.url, u.push))
    val shown      = urlUpdates.lastOption.fold(session.titleShown)(_.title)
    val title      = Option.when(session.currentTitle != shown)(ClientProtocol.Op.Title(session.currentTitle))
    session.titleShown = session.currentTitle
    elements ++ bindings ++ writes ++ history ++ title
  }
}
