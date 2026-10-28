package kreuzberg.engine.server

import kreuzberg.testcore.TestBase
import org.scalatest.BeforeAndAfterAll

class ServerEngineTest extends TestBase with BeforeAndAfterAll {

  private lazy val engine = TestApp.engine(TestServer.config)
  private lazy val server = TestServer.Running(engine.miniServer)

  override protected def afterAll(): Unit = {
    server.close()
    engine.close()
  }

  private def withServer[T](f: TestServer.Client => T): T = server.withClient(f)

  "GET /" should "render the page and create a session" in withServer { client =>
    val response = client.get("/")
    response.status shouldBe 200
    response.setCookie(ServerEngine.SessionCookieName) should not be empty
    response.header("set-cookie").get should (include("HttpOnly") and include("SameSite=Lax"))
    response.body should include("page-home")
    response.body should include("<title>Test - Home</title>")
    // The session id must not be readable by scripts
    response.body should not include client.session.get
  }

  it should "reuse the session on further requests" in withServer { client =>
    client.get("/")
    val session = client.session
    client.get("/counter").setCookie(ServerEngine.SessionCookieName) shouldBe empty
    client.session shouldBe session
  }

  it should "serve the client" in withServer { client =>
    val page   = client.get("/").body
    val script = """<script defer="defer" src="([^"]+)"""".r.findFirstMatchIn(page).get.group(1)
    script should startWith("/assets/kreuzberg/kreuzberg-client.js?hash=")
    val js     = client.get(script)
    js.status shouldBe 200
    js.header("content-type").get should include("javascript")
    js.body should include("/krz/event")
  }

  it should "embed the bindings" in withServer { client =>
    val html     = client.get("/").body
    val bindings = TestServer.initOps(html).asArray.get.head
    bindings.hcursor.get[String]("op") shouldBe Right("bindings")
    val linkId   = """<a href="/counter" data-id="(\d+)"""".r.findFirstMatchIn(html).get.group(1)
    bindings.noSpaces should include(s""""key":"e:${linkId}:click","event":"click","prevent":true""")
  }

  "GET /{path}" should "render deep links" in withServer { client =>
    val response = client.get("/counter")
    response.status shouldBe 200
    response.body should include("page-counter")
    response.body should include("<title>Test - Counter</title>")
  }

  it should "route page loads of an existing session" in withServer { client =>
    client.get("/counter").body should include("page-counter")
    client.get("/form").body should include("page-form")
    client.get("/unknown").body should include("Not Found")
    client.get("/counter").body should include("page-counter")
  }

  it should "respond with the status of the router" in withServer { client =>
    client.get("/counter").status shouldBe 200
    val unknown = client.get("/unknown")
    unknown.status shouldBe 404
    unknown.body should include("Not Found")
    // Also within an existing session, and back again
    client.get("/counter").status shouldBe 200
    client.get("/failing/x").status shouldBe 500
  }

  it should "not render reserved paths and files" in withServer { client =>
    client.get("/assets/unknown.js").status shouldBe 404
    client.get("/unknown.js").status shouldBe 404
  }

  "POST /krz/event" should "run the handler and replace the changed component" in withServer { client =>
    val buttonId = TestServer.dataId(client.get("/counter").body, "counter-inc")
    val response = client.event(listeners = Seq(s"e:${buttonId}:click"))
    response.status shouldBe 200
    val lines    = response.body.linesIterator.toList
    lines.exists(l =>
      l.startsWith("""{"op":"replace"""") && l.contains("""<span id=\"counter-value\">1</span>""")
    ) shouldBe true
    lines.exists(_.startsWith("""{"op":"bindings"""")) shouldBe true
  }

  it should "read inputs and set properties written on the server" in withServer { client =>
    val html     = client.get("/todo").body
    val inputId  = TestServer.dataId(html, "todo-input")
    val buttonId = TestServer.dataId(html, "todo-add")
    val response = client.event(listeners = Seq(s"e:${buttonId}:click"), inputs = Map(inputId -> "buy milk"))
    response.body should include("<li>buy milk</li>")
    response.body should include(s"""{"op":"set","id":${inputId},"property":"value","value":""}""")
  }

  it should "pass keyboard data to handlers" in withServer { client =>
    val inputId              = TestServer.dataId(client.get("/keys").body, "key-input")
    def keyDown(key: String) = client.event(
      listeners = Seq(s"e:${inputId}:keydown"),
      inputs = Map(inputId -> "hello"),
      event = io.circe.Json.obj("type" -> io.circe.Json.fromString("keydown"), "key" -> io.circe.Json.fromString(key))
    )
    keyDown("a").body should not include "hello"
    keyDown("Enter").body should include("""<span id=\"entered\">hello</span>""")
  }

  it should "change the browser history on navigation" in withServer { client =>
    val html     = client.get("/").body
    val linkId   = """<a href="/counter" data-id="(\d+)"""".r.findFirstMatchIn(html).get.group(1)
    val response = client.event(listeners = Seq(s"e:${linkId}:click"))
    response.body should include("page-counter")
    response.body should include("""{"op":"history","title":"Counter","url":"/counter","push":true}""")
    response.body should include("""{"op":"title","title":"Test - Counter"}""")
  }

  it should "use the location of the browser" in withServer { client =>
    val html  = client.get("/").body
    val owner = """"w:(\d+):popstate"""".r.findFirstMatchIn(html).getOrElse(fail("No popstate listener")).group(1)
    client.event(listeners = Seq(s"w:${owner}:popstate"), location = Some("/form")).body should include("page-form")
  }

  it should "ask the browser to reload, if the session expired" in withServer { client =>
    client.withSession(Some("expired"))
    val response = client.event(listeners = Seq("e:1:click"))
    response.status shouldBe 200
    response.body shouldBe """{"reload":true}"""
  }

  it should "be forbidden without a session cookie" in withServer { client =>
    client.event(listeners = Seq("e:1:click")).status shouldBe 403
  }

  it should "reject invalid requests" in withServer { client =>
    client.get("/")
    client.post("/krz/event", "no json").status shouldBe 400
  }

  "GET /krz/stream" should "push results of async work arriving after the event" in withServer { client =>
    val buttonId = TestServer.dataId(client.get("/async").body, "async-start")
    val listener = client.listen()
    val response = client.event(listeners = Seq(s"e:${buttonId}:click"))
    response.body should include("running")
    response.body should not include "done"
    listener.await("""<span id=\"async-result\">done</span>""") should not be empty
  }

  it should "show the loading state of lazy routes, then push the result" in withServer { client =>
    val html     = client.get("/").body
    val linkId   = """<a href="/lazy/x" data-id="(\d+)"""".r.findFirstMatchIn(html).get.group(1)
    val listener = client.listen()
    val response = client.event(listeners = Seq(s"e:${linkId}:click"))
    response.body should include("Loading...")
    response.body should include("""{"op":"history","title":"Lazy...","url":"/lazy/x","push":true}""")
    listener.await("Loaded x") should not be empty
    listener.await(""""title":"Test - Lazy x"""") should not be empty
  }

  it should "push timer ticks, which run on the server" in withServer { client =>
    client.get("/timer").body should not include "\"t:"
    val listener = client.listen()
    listener.await("""<span id=\"ticks\">3</span>""") should not be empty
  }

  it should "forget model values of components which left the page" in withServer { client =>
    val buttonId = TestServer.dataId(client.get("/counter").body, "counter-inc")
    client.event(listeners = Seq(s"e:${buttonId}:click")).body should include("""<span id=\"counter-value\">1</span>""")
    client.get("/").body should include("page-home")
    // Like in the browser: the counter was not subscribed anymore
    client.get("/counter").body should include("""<span id="counter-value">0</span>""")
  }

  it should "not be available without a session" in withServer { client =>
    client.get("/krz/stream?page=x&after=0").status shouldBe 404
  }

  it should "resume after the last received operation" in withServer { client =>
    val html     = client.get("/counter").body
    val pageId   = """data-page="([^"]+)"""".r.findFirstMatchIn(html).get.group(1)
    val buttonId = TestServer.dataId(html, "counter-inc")
    client.event(listeners = Seq(s"e:${buttonId}:click")).body should include("counter-value")
    // A reconnect after operation 1 gets the rest, a fresh one all
    val all      = streamLines(client, pageId, after = 0)
    val rest     = streamLines(client, pageId, after = 1)
    all.size should be > 1
    rest shouldBe all.tail
  }

  it should "tell older pages of the session to stop, and ask them to reload on events" in withServer { client =>
    val first    = client.get("/counter").body
    val pageId   = """data-page="([^"]+)"""".r.findFirstMatchIn(first).get.group(1)
    val buttonId = TestServer.dataId(first, "counter-inc")
    client.get("/counter") // e.g. another tab
    streamLines(client, pageId, after = 0, until = Some("stale")) should not be empty
    client
      .post(
        "/krz/event",
        TestServer.eventBody(listeners = Seq(s"e:${buttonId}:click")),
        EventController.PageHeader -> pageId
      )
      .body shouldBe
      """{"reload":true}"""
  }

  it should "require the page header" in withServer { client =>
    client.get("/")
    client.post("/krz/event", TestServer.eventBody()).status shouldBe 400
  }

  "GET /lazy/{x}" should "render lazy routes completely" in withServer { client =>
    client.get("/lazy/y").body should include("Loaded y")
  }

  "translations" should "use the default locale" in withServer { client =>
    val html = client.get("/language").body
    html should include("""<html lang="en">""")
    html should include("""<span id="greeting">Hello 0</span>""")
  }

  it should "use Accept-Language" in withServer { client =>
    val html = client.get("/language", "Accept-Language" -> "de-DE,de;q=0.9").body
    html should include("""<html lang="de">""")
    html should include("""<span id="greeting">Hallo 0</span>""")
  }

  it should "prefer the locale cookie" in withServer { client =>
    val html = client.get("/language", "Accept-Language" -> "en", "Cookie" -> "lang=de").body
    html should include("""<span id="greeting">Hallo 0</span>""")
  }

  it should "be kept per session" in withServer { german =>
    server.withClient { english =>
      val germanButton  = TestServer.dataId(german.get("/language", "Accept-Language" -> "de").body, "language-inc")
      val englishButton = TestServer.dataId(english.get("/language", "Accept-Language" -> "en").body, "language-inc")
      // Events carry no language, the session's locale is used
      german.event(listeners = Seq(s"e:${germanButton}:click")).body should include("Hallo 1")
      english.event(listeners = Seq(s"e:${englishButton}:click")).body should include("Hello 1")
      german.event(listeners = Seq(s"e:${germanButton}:click")).body should include("Hallo 2")
    }
  }

  it should "store the locale and reload on switchLocale" in withServer { client =>
    val buttonId = TestServer.dataId(client.get("/language").body, "to-german")
    val response = client.event(listeners = Seq(s"e:${buttonId}:click"))
    response.body should include("""{"op":"locale","cookie":"lang","locale":"de"}""")
  }

  /** Lines of a page's stream, until one contains `until` (or the operations are delivered). */
  private def streamLines(
      client: TestServer.Client,
      pageId: String,
      after: Long,
      until: Option[String] = None
  ): Seq[String] = {
    val lines  = scala.collection.mutable.ArrayBuffer[String]()
    val in     = java.net.URI.create(s"${client.baseUrl}/krz/stream?page=${pageId}&after=${after}").toURL.openConnection()
    in.setRequestProperty("Cookie", s"${ServerEngine.SessionCookieName}=${client.session.get}")
    in.setReadTimeout(1000)
    val reader = java.io.BufferedReader(java.io.InputStreamReader(in.getInputStream))
    try {
      Iterator.continually(reader.readLine()).takeWhile(_ != null).foreach { line =>
        lines += line
        if (until.exists(line.contains)) throw StopReading()
      }
    } catch {
      case _: StopReading | _: java.net.SocketTimeoutException => ()
    } finally {
      reader.close()
    }
    lines.toSeq.filterNot(_.contains("\"ping\""))
  }

  private class StopReading extends RuntimeException

  "isPagePath" should "accept paths without extension and HTML files" in {
    ServerEngine.isPagePath(List("todo")) shouldBe true
    ServerEngine.isPagePath(List("lazy", "foo")) shouldBe true
    ServerEngine.isPagePath(List("page.html")) shouldBe true
    ServerEngine.isPagePath(List("page.HTM")) shouldBe true
  }

  it should "reject reserved prefixes, files and empty paths" in {
    ServerEngine.isPagePath(List("krz", "event")) shouldBe false
    ServerEngine.isPagePath(List("api", "todo")) shouldBe false
    ServerEngine.isPagePath(List("assets", "main.js")) shouldBe false
    ServerEngine.isPagePath(List("favicon.ico")) shouldBe false
    ServerEngine.isPagePath(Nil) shouldBe false
  }
}
