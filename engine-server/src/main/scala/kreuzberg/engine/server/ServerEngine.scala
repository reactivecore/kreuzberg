package kreuzberg.engine.server

import kreuzberg.*
import kreuzberg.extras.{UrlPath, UrlResource}
import kreuzberg.i18n.Translations
import kreuzberg.miniserver.*
import kreuzberg.miniserver.MiniServer.MiniServerEndpoint
import org.slf4j.LoggerFactory
import sttp.model.headers.Cookie.SameSite
import sttp.model.headers.{Cookie, CookieWithMeta}
import sttp.model.headers.CacheDirective
import sttp.model.{Header, MediaType, StatusCode}
import sttp.tapir.*
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.OnDecodeFailure.*

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.*

/**
 * Server-side rendering engine.
 *
 * Components are assembled and rendered on the server, one tree per browser session. The browser runs a small client
 * (`kreuzberg-client.js`), which forwards the bound events in order and applies the returned operations (see
 * [[ClientProtocol]]). Results of async work arriving between events are pushed via a long-lived stream.
 *
 * Translations (`MiniServerConfig.translations`) are resolved per page load (cookie, then `Accept-Language`) and kept
 * per session; `Translations.switchLocale` stores the locale in the cookie and reloads the page. Init data
 * (`MiniServerConfig.init`) is not supported.
 *
 * @param component
 *   root component, evaluated per session
 * @param serviceRepositoryFactory
 *   creates the services of a new session
 * @param config
 *   server configuration; the client script is added to the assets
 * @param sessionExpiry
 *   sessions are removed after this time of inactivity
 */
class ServerEngine(
    component: => Component,
    serviceRepositoryFactory: () => ServiceRepository,
    config: MiniServerConfig,
    sessionExpiry: FiniteDuration = 1.hour
) extends AutoCloseable {
  private val logger = LoggerFactory.getLogger(getClass)

  private val deploymentConfig: DeploymentConfig = config.deployment.copy(
    assetPaths = AssetPaths(ServerEngine.ClientAsset +: config.deployment.assetPaths.assetPaths)
  )

  private val sessionManager = SessionManager(component, serviceRepositoryFactory, sessionExpiry)

  private val clientUrl: String = deploymentConfig.hashedUrl(ServerEngine.ClientPath)

  private val translationProvider: Option[TranslationProvider] = config.translations.map(TranslationProvider.load(_))

  /** Translations by locale, created once. */
  private val translationsByLocale = TrieMap[String, Translations]()

  if (config.init.nonEmpty) {
    logger.warn("Init data is not supported by the ServerEngine and will be ignored")
  }

  if (translationProvider.nonEmpty && Index.hasLangAttribute(config.deployment.htmlRootAttributes)) {
    logger.error(
      "Translations are configured, but DeploymentConfig.htmlRootAttributes also sets lang. " +
        "Remove lang from htmlRootAttributes, the language is set per request."
    )
  }

  /** The MiniServer, serving pages, events, assets and the API. */
  lazy val miniServer: MiniServer = {
    val eventEndpoints = EventController(sessionManager, config.translations.map(_.cookieName)).endpoints
    // Pages are rendered here, so the MiniServer needs no translations
    new MiniServer(config.copy(deployment = deploymentConfig, translations = None)) {
      override protected def primaryEndpoints: List[MiniServerEndpoint] =
        List(assetHandler, pageHandler) ++ apiEndpointHandler.toList ++ eventEndpoints

      override protected def secondaryEndpoints: List[MiniServerEndpoint] =
        List(otherPageHandler, rootAssetHandler)
    }
  }

  /** Run and block forever. */
  def run(): Unit = miniServer.run()

  override def close(): Unit = sessionManager.close()

  private val pageHandler: MiniServerEndpoint = pageEndpoint
    .in("")
    .in(queryParams)
    .in(headers)
    .in(cookies)
    .handleSuccess { case (query, headers, cookies) =>
      renderPage(UrlResource(UrlPath(), query.toSeq), headers, cookies)
    }

  /** Pages of other paths, for routing. */
  private val otherPageHandler: MiniServerEndpoint = pageEndpoint
    .in(
      paths
        .validate(Validator.custom[List[String]] { parts =>
          ValidationResult.validWhen(ServerEngine.isPagePath(parts))
        })
        .onDecodeFailureNextEndpoint
    )
    .in(queryParams)
    .in(headers)
    .in(cookies)
    .errorOut(statusCode)
    .handle { case (parts, query, headers, cookies) =>
      Right(renderPage(UrlResource(UrlPath(parts), query.toSeq), headers, cookies))
    }

  private def pageEndpoint = endpoint.get
    .out(htmlBodyUtf8)
    .out(setCookies)
    .out(header(Header.contentType(MediaType.TextHtml)))
    // The page belongs to the session's current state, it must not come from a cache (e.g. on back navigation)
    .out(header(Header.cacheControl(CacheDirective.NoStore)))
    // As decided by the router, e.g. 404 for unknown pages
    .out(statusCode)

  private def renderPage(
      url: UrlResource,
      headers: List[Header],
      cookies: List[Cookie]
  ): (String, List[CookieWithMeta], StatusCode) = {
    val cookieId                           = cookies.find(_.name == ServerEngine.SessionCookieName).map(_.value)
    val translations                       = resolveTranslations(headers, cookies)
    val ((html, sessionId, status), isNew) = sessionManager.openPage(cookieId, url, translations) { session =>
      // Redirects during assembly (e.g. a forwarding route) only have to update the address bar.
      val history = session.takePendingUrlUpdates().map(u => ClientProtocol.Op.History(u.title, u.url, u.push))
      val writes  = session.takePendingWrites().map(w => ClientProtocol.Op.Set(w.id, w.property, w.value))
      val lang    = Option.when(translationProvider.nonEmpty)(translations.locale)
      val html    = ClientRenderer.renderPage(session, deploymentConfig, clientUrl, history ++ writes, lang)
      (html, session.id, StatusCode(session.statusCode))
    }
    (html, Option.when(isNew)(ServerEngine.sessionCookie(sessionId)).toList, status)
  }

  private def resolveTranslations(headers: List[Header], cookies: List[Cookie]): Translations = {
    translationProvider.fold(Translations.empty) { provider =>
      val request = InitRequest(headers.map(h => h.name -> h.value), cookies.map(c => c.name -> c.value))
      val page    = provider.forRequest(request)
      translationsByLocale.getOrElseUpdate(
        page.locale,
        Translations.fromMap(page.locale, page.available, page.messages)
      )
    }
  }
}

object ServerEngine {

  /** Name of the HttpOnly cookie holding the session id. */
  val SessionCookieName: String = "KRZ_SESSION"

  /** Asset path of the browser client. */
  val ClientPath: String = "kreuzberg/kreuzberg-client.js"

  private val ClientAsset: RestrictedAssetCandidatePath =
    RestrictedAssetCandidatePath(None, AssetCandidatePath.Resource("kreuzberg/engine/server/client/", "kreuzberg/"))

  /** First path segments, which never belong to pages. */
  private val ReservedFirstSegment: Set[String] = Set("krz", "api", "assets")

  /**
   * The session cookie. `Secure` is not set, so that it works with plain HTTP during development; it can be added by a
   * reverse proxy.
   */
  def sessionCookie(sessionId: String): CookieWithMeta = {
    CookieWithMeta.unsafeApply(
      name = SessionCookieName,
      value = sessionId,
      path = Some("/"),
      httpOnly = true,
      sameSite = Some(SameSite.Lax)
    )
  }

  /** True if a path is rendered as page: not reserved, and either without extension or `.html`/`.htm`. */
  def isPagePath(parts: List[String]): Boolean = {
    parts.headOption.exists(first => !ReservedFirstSegment.contains(first)) && {
      val last = parts.last
      last.lastIndexOf('.') match {
        case -1  => true
        case dot => Set("html", "htm").contains(last.drop(dot + 1).toLowerCase)
      }
    }
  }
}
