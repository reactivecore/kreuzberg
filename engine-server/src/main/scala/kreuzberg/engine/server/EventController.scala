package kreuzberg.engine.server

import kreuzberg.extras.UrlResource
import kreuzberg.miniserver.MiniServer.MiniServerEndpoint
import org.slf4j.LoggerFactory
import ox.Chunk
import ox.flow.Flow
import sttp.model.headers.Cookie
import sttp.model.{MediaType, StatusCode}
import sttp.tapir.*
import sttp.tapir.server.model.EndpointExtensions.*
import sttp.tapir.server.netty.sync.OxStreams

import java.nio.charset.StandardCharsets

/**
 * Endpoints of `kreuzberg-client.js`.
 *
 *   - `POST /krz/event`: fire listeners of the page. The resulting operations are published to the page.
 *   - `GET /krz/stream?page=..&after=..`: the page's operations in order, one JSON object per line. Also processes
 *     async work arriving between events (e.g. a `Future` completing on the component's execution context).
 *
 * Operations are only sent by the stream, so they cannot be reordered. Nothing is sent while holding the session lock,
 * so a slow connection cannot block the session.
 */
private[server] class EventController(sessionManager: SessionManager, localeCookie: Option[String]) {
  import EventController.*

  def endpoints: List[MiniServerEndpoint] = List(eventHandler, streamHandler)

  private val eventHandler: MiniServerEndpoint = endpoint.post
    .in("krz" / "event")
    // A custom header can't be sent cross-site without CORS, which protects against CSRF
    .in(header[String](PageHeader))
    .in(stringBody)
    .maxRequestBodyLength(MaxRequestBytes)
    .in(cookies)
    .errorOut(statusCode)
    .out(stringJsonBody)
    .handle { case (pageId, body, cookies) =>
      ClientProtocol.EventRequest.parse(body) match {
        case None          => Left(StatusCode.BadRequest)
        case Some(request) =>
          sessionId(cookies) match {
            case None     => Left(StatusCode.Forbidden)
            case Some(id) =>
              sessionManager.get(id) match {
                // Session expired, reloading creates a new one
                case None          => Right(ReloadResponse)
                case Some(session) =>
                  val response = session.withLock {
                    if (session.page.id != pageId) {
                      // A newer page of the session was loaded, e.g. in another tab
                      ReloadResponse
                    } else {
                      session.page.confirm(request.received)
                      session.inputs = request.inputsById
                      request.location.foreach(l => session.currentUrl = UrlResource(l))
                      process(session)(fire(session, request))
                      s"""{"seq":${session.page.lastSeq}}"""
                    }
                  }
                  Right(response)
              }
          }
      }
    }

  private val streamHandler: MiniServerEndpoint = endpoint.get
    .in("krz" / "stream")
    .in(query[String]("page"))
    .in(query[Long]("after"))
    .in(cookies)
    .errorOut(statusCode)
    .out(operationStream)
    .handle { case (pageId, after, cookies) =>
      sessionId(cookies).flatMap(sessionManager.get) match {
        case None          => Left(StatusCode.NotFound)
        case Some(session) => Right(stream(session, pageId, after))
      }
    }

  /** Run `f` and publish the resulting operations of each processing step. Must hold the session lock. */
  private def process(session: Session)(f: => Unit): Unit = {
    session.run(f)
    session.settle() { changes => session.publish(ClientRenderer.renderStep(session, changes)) }
    for {
      cookie <- localeCookie
      locale <- session.takePendingLocale()
    } session.publish(Seq(ClientProtocol.Op.Locale(cookie, locale)))
  }

  private def fire(session: Session, request: ClientProtocol.EventRequest): Unit = {
    lazy val event = MockEvent(request.event)
    request.listeners.distinct.foreach { key =>
      ClientProtocol.parseListenerKey(key).flatMap(session.bindings.listeners.get) match {
        case Some(listener) => listener.handlers.foreach(_(event))
        // Can happen if the page changed in between
        case None           => logger.debug(s"No listener ${key} in session ${session.id}")
      }
    }
  }

  /**
   * Stream the operations of a page after `after`, and process async work. Ends when the page is replaced, the session
   * expired or the client disconnected.
   */
  private def stream(session: Session, pageId: String, after: Long): OperationStream = Flow.usingEmit { emit =>
    def send(lines: Seq[String]): Unit = emit(Chunk.fromArray(lines.mkString.getBytes(StandardCharsets.UTF_8)))

    var sent    = after // scalafix:ok
    var running = true  // scalafix:ok
    // Operations up to `after` arrived
    session.page.confirm(after)
    while (running) {
      val page = session.page
      if (page.id != pageId) {
        send(Seq(ClientProtocol.line(ClientProtocol.Op.Stale)))
        running = false
      } else if (!sessionManager.get(session.id).contains(session)) {
        send(Seq(ClientProtocol.line(ClientProtocol.Op.Reload)))
        running = false
      } else {
        page.after(sent) match {
          case None                          =>
            // Fell behind too far, the page has to be rebuilt
            send(Seq(ClientProtocol.line(ClientProtocol.Op.Reload)))
            running = false
          case Some(lines) if lines.nonEmpty =>
            send(lines)
            sent += lines.size
          case _                             =>
            if (session.changer.hasPending) {
              session.withLock {
                if (session.page eq page) process(session)(())
              }
            } else {
              val active = session.awaitActivity(PingIntervalMillis) {
                page.hasAfter(sent) || session.changer.hasPending || (session.page ne page)
              }
              if (!active) {
                send(Seq(ClientProtocol.line(ClientProtocol.Op.Ping)))
                // An open page keeps its session
                session.touch()
              }
            }
        }
      }
    }
  }

  private def sessionId(cookies: List[Cookie]): Option[String] = {
    cookies.find(_.name == ServerEngine.SessionCookieName).map(_.value)
  }

  private def operationStream = streamBody(OxStreams)(Schema.string, NdJson, Some(StandardCharsets.UTF_8))
}

private[server] object EventController {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Header carrying the page id of an event. */
  val PageHeader = "X-Kreuzberg-Page"

  /** Response body: operations, one JSON object per line. */
  type OperationStream = Flow[Chunk[Byte]]

  private val ReloadResponse = """{"reload":true}"""

  private val MaxRequestBytes: Long = 1024L * 1024L

  /** One JSON object per line. */
  private object NdJson extends CodecFormat {
    override val mediaType: MediaType = MediaType("application", "x-ndjson")
  }

  /** Pings keep the stream open through proxies and detect closed connections. */
  private val PingIntervalMillis: Long = 25_000L
}
