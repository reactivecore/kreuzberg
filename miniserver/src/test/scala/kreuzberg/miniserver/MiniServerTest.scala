package kreuzberg.miniserver

import kreuzberg.testcore.{ShutdownSupport, TestBase}
import scalatags.Text.tags2
import kreuzberg.scalatags.*
import kreuzberg.*
import kreuzberg.rpc.{Dispatcher, UnknownCallError}
import org.slf4j.LoggerFactory
import sttp.shared.Identity
import ox.*
import sttp.client3.*
import sttp.model.{StatusCode, Uri}

import java.nio.charset.StandardCharsets
import scala.util.Using

class MiniServerTest extends TestBase with ShutdownSupport {
  val logger = LoggerFactory.getLogger(getClass)

  trait Env {
    def deploymentType: DeploymentType = DeploymentType.Debug

    val deployment = DeploymentConfig(
      deploymentType = deploymentType,
      assetPaths = AssetPaths(
        Seq(
          AssetCandidatePath.Resource("test_assets/", "foo/")
        )
      ),
      rootAssets = AssetPaths(
        Seq(
          AssetCandidatePath.Resource("test_root_assets/")
        )
      ),
      extraHtmlHeader = Seq(
        tags2.title("Hello MiniServer")
      )
    )

    object helloApi extends Dispatcher[Identity] {
      override def handles(serviceName: String): Boolean = serviceName == "hello"

      override def call(serviceName: String, name: String, request: rpc.Request): Identity[rpc.Response] = {
        if (serviceName == "hello" && name == "ping") {
          rpc.Response.build(42)
        } else {
          throw UnknownCallError(serviceName, name)
        }
      }
    }

    val config = MiniServerConfig(
      deployment,
      port = 0,
      host = "127.0.0.1",
      api = Some(helloApi),
      translations = Some(
        TranslationConfig(Seq("test_i18n/test.default.rctr", "test_i18n/test.de.rctr"))
      ),
      content = Some(ContentConfig(Seq("test_content/about")))
    )

    val miniServer               = new MiniServer(config, fastShutdown = true)
    val (miniServerStopFn, port) = OxServiceBox.run {
      val running = miniServer.start()
      releaseAfterScope {
        val t0 = System.currentTimeMillis()
        running.stop()
        val t1 = System.currentTimeMillis()
        logger.info(s"Stop took ${t1 - t0}ms")
      }
      running.port
    }
    logger.info(s"Running on port ${port}")

    withShutdown {
      miniServerStopFn()
    }

    val rootUrl = s"http://localhost:${port}"
    val backend = HttpClientSyncBackend()
    withShutdown {
      backend.close()
    }

    /**
     * Sends a GET request over a plain socket and returns the status line and headers. The Java HttpClient hides some
     * headers (e.g. Content-Length) and merges others.
     */
    def rawResponseHead(path: String, headers: String*): List[String] = {
      Using.resource(new java.net.Socket("127.0.0.1", port)) { socket =>
        val request  =
          (s"GET ${path} HTTP/1.1" :: "Host: localhost" :: "Connection: close" :: headers.toList).mkString("\r\n")
        socket.getOutputStream.write((request + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
        val response = new String(socket.getInputStream.readAllBytes(), StandardCharsets.US_ASCII)
        response.split("\r\n\r\n", 2).head.split("\r\n").toList
      }
    }
  }

  it should "serve index.html" in new Env {
    val result = basicRequest.get(uri"${rootUrl}").send(backend)
    result.code shouldBe StatusCode.Ok
    result.body.value should include("Hello MiniServer")
    result.header("Content-Type") shouldBe Some("text/html; charset=UTF-8")
  }

  it should "deliver content" in new Env {
    def content(name: String, locale: Option[String]) =
      basicRequest.get(uri"${rootUrl}/krz/content?name=${name}&locale=${locale}").send(backend)

    val german = content("test_content/about", Some("de"))
    german.body.value shouldBe "<p>Deutsch</p>\n"
    german.header("Content-Type") shouldBe Some("text/html; charset=UTF-8")
    content("test_content/about", Some("en")).body.value shouldBe "<p>English</p>\n"
    content("test_content/about", None).body.value shouldBe "<p>English</p>\n"
    content("test_content/unknown", Some("de")).code shouldBe StatusCode.NotFound
  }

  it should "deliver translations with the index page" in new Env {
    val english = basicRequest.get(uri"${rootUrl}").send(backend).body.value
    english should include("<html lang=\"en\">")
    english should include(
      """window.kreuzbergTranslations = {"locale":"en","available":["en","de"],"cookie":"lang","""
    )
    english should include("\"greeting\":\"Hello %1\"")
    english should include("<\\/script>")
    english should not include ("</script>\"")

    val byCookie = basicRequest.get(uri"${rootUrl}/other").cookie("lang", "de").send(backend).body.value
    byCookie should include("<html lang=\"de\">")
    byCookie should include("\"greeting\":\"Hallo %1\"")
    byCookie should include("\"script.close\":\"<\\/script>\"")

    val byHeader =
      basicRequest.get(uri"${rootUrl}").header("Accept-Language", "fr-CH, de;q=0.9, en;q=0.8").send(backend).body.value
    byHeader should include("<html lang=\"de\">")
  }

  it should "serve index html on other places" in new Env {
    val result = basicRequest.get(uri"${rootUrl}/other").send(backend)
    result.code shouldBe StatusCode.Ok
    result.body.value should include("Hello MiniServer")
  }

  it should "not serve index html on suffixed places" in new Env {
    val result = basicRequest.get(uri"${rootUrl}/other.png").send(backend)
    result.code shouldBe StatusCode.NotFound
  }

  it should "serve API requests" in new Env {
    val result = basicRequest
      .post(uri"${rootUrl}/api/hello/ping")
      .body("{}")
      .send(backend)
    result.code shouldBe StatusCode.Ok
    result.body.value shouldBe "42"

    withClue("other routes are mapped to 404") {
      basicRequest
        .post(uri"${rootUrl}/api/hello/unknown")
        .body("{}")
        .send(backend)
        .code shouldBe StatusCode.NotFound
    }

    withClue("other routes are mapped to 404") {
      basicRequest
        .post(uri"${rootUrl}/api/unknown/ping")
        .body("{}")
        .send(backend)
        .code shouldBe StatusCode.NotFound
    }
  }

  it should "serve assets" in new Env {
    val result = basicRequest.get(uri"${rootUrl}/assets/foo/asset1.txt").send(backend)
    result.code shouldBe StatusCode.Ok
    result.body.value should include("I am an Asset!")
    result.contentType shouldBe Some("text/plain")

    withClue("Other assets are not located") {
      basicRequest.get(uri"${rootUrl}/assets/foo/missing.txt").send(backend).code shouldBe StatusCode.NotFound
    }
    withClue("It should not escape it's path") {
      basicRequest
        .get(uri"${rootUrl}/assets/foo/../test_invisible/do_not_serve.txt")
        .send(backend)
        .code shouldBe StatusCode.NotFound
    }
  }

  trait ProductionEnv extends Env {
    override def deploymentType: DeploymentType = DeploymentType.Production
  }

  it should "answer assets with 304 if the ETag matches" in new Env {
    val first = basicRequest.get(uri"${rootUrl}/assets/foo/asset1.txt").send(backend)
    first.code shouldBe StatusCode.Ok
    val etag  = first.header("ETag").value
    etag should fullyMatch regex "\"[0-9a-f]{64}\""
    first.header("Cache-Control") shouldBe Some(AssetHandler.RevalidateCacheControl)
    first.header("Vary") shouldBe Some("Accept-Encoding")

    val second = basicRequest.get(uri"${rootUrl}/assets/foo/asset1.txt").header("If-None-Match", etag).send(backend)
    second.code shouldBe StatusCode.NotModified
    second.header("ETag") shouldBe Some(etag)
    second.header("Cache-Control") shouldBe Some(AssetHandler.RevalidateCacheControl)
    second.body shouldBe Left("")

    withClue("the 304 has the Content-Length of the full response (RFC 9110, 8.6)") {
      val raw = rawResponseHead("/assets/foo/asset1.txt", s"If-None-Match: ${etag}")
      raw.head shouldBe "HTTP/1.1 304 Not Modified"
      raw.map(_.toLowerCase) should contain("content-length: 15")
    }

    withClue("multiple If-None-Match headers are allowed") {
      val raw = rawResponseHead("/assets/foo/asset1.txt", "If-None-Match: \"other\"", s"If-None-Match: ${etag}")
      raw.head shouldBe "HTTP/1.1 304 Not Modified"
    }

    withClue("weak ETags, e.g. from a compressing proxy, match too") {
      basicRequest
        .get(uri"${rootUrl}/assets/foo/asset1.txt")
        .header("If-None-Match", s"W/${etag}")
        .send(backend)
        .code shouldBe StatusCode.NotModified
    }

    withClue("other ETags are served in full") {
      val other =
        basicRequest.get(uri"${rootUrl}/assets/foo/asset1.txt").header("If-None-Match", "\"other\"").send(backend)
      other.code shouldBe StatusCode.Ok
      other.body.value shouldBe "I am an Asset!\n"
    }

    withClue("root assets have ETags too") {
      basicRequest.get(uri"${rootUrl}/favicon.png").send(backend).header("ETag") shouldBe defined
    }
  }

  it should "use another ETag for the Brotli variant" in new Env {
    def get(acceptEncoding: String, ifNoneMatch: Option[String] = None) = basicRequest
      .get(uri"${rootUrl}/assets/foo/compressed.txt")
      .header("Accept-Encoding", acceptEncoding, replaceExisting = true)
      .header("If-None-Match", ifNoneMatch)
      .disableAutoDecompression
      .response(asByteArray)
      .send(backend)

    val plain  = get("gzip")
    plain.header("Content-Encoding") shouldBe None
    val brotli = get("gzip, br")
    brotli.header("Content-Encoding") shouldBe Some("br")
    brotli.body.value shouldBe getClass.getClassLoader
      .getResourceAsStream("test_assets/compressed.txt.br")
      .readAllBytes()

    val plainEtag            = plain.header("ETag").value
    val brotliEtag           = brotli.header("ETag").value
    def hashOf(name: String) = AssetCandidatePath.Resource("test_assets/").locate(name).value.hashSha256()
    plainEtag shouldBe s"\"${hashOf("compressed.txt")}\""
    brotliEtag shouldBe s"\"${hashOf("compressed.txt.br")}\""

    get("gzip, br", Some(brotliEtag)).code shouldBe StatusCode.NotModified
    get("gzip, br", Some(plainEtag)).code shouldBe StatusCode.Ok
    get("gzip", Some(brotliEtag)).code shouldBe StatusCode.Ok
  }

  it should "not cache hashed assets forever in debug mode" in new Env {
    val hashed = deployment.hashedUrl("foo/asset1.txt")
    val result = basicRequest.get(Uri.unsafeParse(rootUrl + hashed)).send(backend)
    result.code shouldBe StatusCode.Ok
    result.header("Cache-Control") shouldBe Some(AssetHandler.RevalidateCacheControl)
  }

  it should "cache assets with the current hash forever in production" in new ProductionEnv {
    val hashed = deployment.hashedUrl("foo/asset1.txt")
    hashed should startWith("/assets/foo/asset1.txt?hash=")
    val result = basicRequest.get(Uri.unsafeParse(rootUrl + hashed)).send(backend)
    result.code shouldBe StatusCode.Ok
    result.header("Cache-Control") shouldBe Some(AssetHandler.ImmutableCacheControl)

    withClue("an outdated hash must be revalidated") {
      basicRequest
        .get(uri"${rootUrl}/assets/foo/asset1.txt?hash=outdated")
        .send(backend)
        .header("Cache-Control") shouldBe Some(AssetHandler.RevalidateCacheControl)
    }
    withClue("no hash must be revalidated") {
      basicRequest
        .get(uri"${rootUrl}/assets/foo/asset1.txt")
        .send(backend)
        .header("Cache-Control") shouldBe Some(AssetHandler.RevalidateCacheControl)
    }
  }

  it should "serve extra asserts" in new Env {
    val result = basicRequest.get(uri"${rootUrl}/favicon.png").response(asByteArray).send(backend)
    result.code shouldBe StatusCode.Ok
    result.contentType shouldBe Some("image/png")
    result.body.value.length shouldBe >(10)
  }
}
