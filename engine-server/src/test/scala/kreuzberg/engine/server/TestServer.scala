package kreuzberg.engine.server

import io.circe.Json
import kreuzberg.miniserver.{DeploymentConfig, DeploymentType, MiniServer, MiniServerConfig}
import ox.{never, supervised}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.{CompletableFuture, LinkedBlockingQueue, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Runs a server on a random port. */
object TestServer {

  /** Configuration on localhost, with a random port. */
  val config: MiniServerConfig = MiniServerConfig(
    DeploymentConfig(deploymentType = DeploymentType.Debug),
    host = "127.0.0.1",
    port = 0
  )

  /** A started server, stopped on close. Starting and stopping takes a few seconds, so share it if possible. */
  class Running(server: MiniServer) extends AutoCloseable {
    private val started = CompletableFuture[Int]()
    private val thread  = Thread
      .ofPlatform()
      .daemon()
      .name("test-server")
      .start { () =>
        try {
          supervised {
            val binding = server.start()
            started.complete(binding.port)
            try never
            finally binding.stop()
          }
        } catch {
          case _: InterruptedException => () // stopped
          case NonFatal(e)             => started.completeExceptionally(e)
        }
      }

    val baseUrl: String = s"http://127.0.0.1:${started.get(30, TimeUnit.SECONDS)}"

    /** Run with a fresh client, i.e. a new session. */
    def withClient[T](f: Client => T): T = {
      val client = Client(baseUrl)
      try f(client)
      finally client.close()
    }

    override def close(): Unit = {
      thread.interrupt()
      thread.join(30_000)
    }
  }

  case class Response(status: Int, body: String, headers: Map[String, Seq[String]]) {
    def header(name: String): Option[String] = headers.get(name.toLowerCase).flatMap(_.headOption)

    /** Value of a cookie, set by this response. */
    def setCookie(name: String): Option[String] = {
      headers.getOrElse("set-cookie", Nil).collectFirst {
        case line if line.startsWith(name + "=") => line.stripPrefix(name + "=").takeWhile(_ != ';')
      }
    }
  }

  /** Minimal HTTP client, keeping the session cookie. */
  case class Client(baseUrl: String) extends AutoCloseable {
    private val http                      = HttpClient.newHttpClient()
    private var sessionId: Option[String] = None // scalafix:ok

    def session: Option[String] = sessionId

    /** Forget the session cookie, or use a different one. */
    def withSession(id: Option[String]): Client = {
      sessionId = id
      this
    }

    // Not close(): it waits for open requests, e.g. of the push channel
    override def close(): Unit = http.shutdownNow()

    /** Page of the last loaded HTML and number of the last received operation, like `kreuzberg-client.js`. */
    private var pageId: String = "" // scalafix:ok
    private var lastSeq: Long  = 0  // scalafix:ok

    /**
     * Post an event like `kreuzberg-client.js`. On success, the body contains the resulting operations, read from the
     * page's stream (without their numbers); otherwise the response of the post.
     */
    def event(
        listeners: Seq[String] = Nil,
        inputs: Map[Int, String] = Map.empty,
        event: Json = Json.obj("type" -> Json.fromString("click")),
        location: Option[String] = None
    ): Response = {
      val builder  = HttpRequest
        .newBuilder()
        .POST(HttpRequest.BodyPublishers.ofString(eventBody(listeners, inputs, event, location)))
        .header("Content-Type", "application/json")
        .header(EventController.PageHeader, pageId)
      val response = send(builder, "/krz/event")
      io.circe.parser.parse(response.body).toOption.flatMap(_.hcursor.get[Long]("seq").toOption) match {
        case Some(seq) => response.copy(body = readStream(seq).mkString("\n"))
        case None      => response
      }
    }

    /** Read the page's stream until operation `untilSeq`. */
    private def readStream(untilSeq: Long): Seq[String] = {
      if (untilSeq <= lastSeq) {
        Nil
      } else {
        val lines  = stream().iterator().asScala
        val result = Seq.newBuilder[String]
        while (lastSeq < untilSeq && lines.hasNext) {
          val line = lines.next()
          SeqPattern.findFirstMatchIn(line).map(_.group(1).toLong).filter(_ > 0).foreach { seq =>
            lastSeq = seq
            result += SeqPattern.replaceAllIn(line, "")
          }
        }
        result.result()
      }
    }

    private def stream(): java.util.stream.Stream[String] = {
      val builder =
        HttpRequest.newBuilder().GET().uri(URI.create(s"${baseUrl}/krz/stream?page=${pageId}&after=${lastSeq}"))
      sessionId.foreach(id => builder.header("Cookie", s"${ServerEngine.SessionCookieName}=${id}"))
      http.send(builder.build(), HttpResponse.BodyHandlers.ofLines()).body()
    }

    /** Listen on the page's stream; lines are collected in the background until the client is closed. */
    def listen(): Listener = {
      val builder  =
        HttpRequest.newBuilder().GET().uri(URI.create(s"${baseUrl}/krz/stream?page=${pageId}&after=${lastSeq}"))
      sessionId.foreach(id => builder.header("Cookie", s"${ServerEngine.SessionCookieName}=${id}"))
      val listener = Listener()
      http
        .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofLines())
        .thenAccept(response => response.body().forEach(line => listener.lines.put(line)))
      listener
    }

    def get(path: String, headers: (String, String)*): Response = {
      val builder = HttpRequest.newBuilder().GET()
      headers.foreach { case (k, v) => builder.header(k, v) }
      send(builder, path)
    }

    def post(path: String, body: String = "{}", headers: (String, String)*): Response = {
      val builder = HttpRequest
        .newBuilder()
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .header("Content-Type", "application/json")
      headers.foreach { case (k, v) => builder.header(k, v) }
      send(builder, path)
    }

    private def send(builder: HttpRequest.Builder, path: String): Response = {
      sessionId.foreach(id => builder.header("Cookie", s"${ServerEngine.SessionCookieName}=${id}"))
      val response = http.send(builder.uri(URI.create(baseUrl + path)).build(), HttpResponse.BodyHandlers.ofString())
      val headers  = response.headers().map().asScala.map { case (k, v) => k.toLowerCase -> v.asScala.toSeq }.toMap
      val result   = Response(response.statusCode(), response.body(), headers)
      result.setCookie(ServerEngine.SessionCookieName).foreach(id => sessionId = Some(id))
      PagePattern.findFirstMatchIn(result.body).foreach { m =>
        pageId = m.group(1)
        lastSeq = 0
      }
      result
    }
  }

  private val SeqPattern  = ""","seq":(\d+)""".r
  private val PagePattern = """<div id="root" data-page="([^"]+)"""".r

  /** Lines of the push channel. */
  case class Listener(lines: LinkedBlockingQueue[String] = LinkedBlockingQueue()) {

    /** Wait for a line containing `text`. */
    def await(text: String, timeoutMillis: Long = 5000): Option[String] = {
      val deadline = System.currentTimeMillis() + timeoutMillis
      Iterator
        .continually(lines.poll(math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS))
        .takeWhile(_ => System.currentTimeMillis() < deadline)
        .find(line => line != null && line.contains(text))
    }
  }

  def eventBody(
      listeners: Seq[String] = Nil,
      inputs: Map[Int, String] = Map.empty,
      event: Json = Json.obj("type" -> Json.fromString("click")),
      location: Option[String] = None
  ): String = {
    Json
      .obj(
        "listeners" -> Json.fromValues(listeners.map(Json.fromString)),
        "event"     -> event,
        "inputs"    -> Json.fromFields(inputs.map { case (id, value) =>
          id.toString -> Json.obj("value" -> Json.fromString(value), "checked" -> Json.False)
        }),
        "location"  -> location.fold(Json.Null)(Json.fromString)
      )
      .noSpaces
  }

  /** The operations embedded into a page. */
  def initOps(html: String): Json = {
    val json = """<script type="application/json" id="krz-init">(.*?)</script>""".r
      .findFirstMatchIn(html)
      .getOrElse(throw new NoSuchElementException("No init operations"))
      .group(1)
    io.circe.parser.parse(json).fold(throw _, identity)
  }

  /** Find the `data-id` of the element with the given HTML id. */
  def dataId(html: String, elementId: String): Int = {
    val tag = s"""<[^>]*id="${elementId}"[^>]*>""".r.findFirstIn(html).getOrElse {
      throw new NoSuchElementException(s"No element ${elementId}")
    }
    """data-id="(\d+)"""".r.findFirstMatchIn(tag).map(_.group(1).toInt).getOrElse {
      throw new NoSuchElementException(s"No data-id on ${elementId}")
    }
  }
}
