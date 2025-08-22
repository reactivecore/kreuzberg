package kreuzberg.miniserver

import kreuzberg.testcore.{ShutdownSupport, TestBase}
import scalatags.Text.tags2
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.*
import kreuzberg.rpc.{Dispatcher, UnknownCallError}
import org.slf4j.LoggerFactory
import sttp.shared.Identity
import ox.*
import sttp.client3.*
import sttp.model.StatusCode

class MiniServerTest extends TestBase with ShutdownSupport {
  val logger = LoggerFactory.getLogger(getClass)

  trait Env {
    val deployment = DeploymentConfig(
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
      api = Some(helloApi)
    )

    val miniServer               = new MiniServer(config)
    val (miniServerStopFn, port) = OxServiceBox.run {
      val running = miniServer.start(fastShutdown = true)
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
  }

  it should "serve index.html" in new Env {
    val result = basicRequest.get(uri"${rootUrl}").send(backend)
    result.code shouldBe StatusCode.Ok
    result.body.value should include("Hello MiniServer")
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

  it should "serve extra asserts" in new Env {
    val result = basicRequest.get(uri"${rootUrl}/favicon.png").response(asByteArray).send(backend)
    result.code shouldBe StatusCode.Ok
    result.contentType shouldBe Some("image/png")
    result.body.value.length shouldBe >(10)
  }
}
