package kreuzberg.engine.server.e2e

import com.microsoft.playwright.{BrowserType, Page, Playwright}
import kreuzberg.engine.server.TestServer
import kreuzberg.engine.server.e2e.PlaywrightFixture.Target
import kreuzberg.miniserver.MiniServer
import org.scalatest.{BeforeAndAfterAll, Outcome, TestSuite, TestSuiteMixin}

import scala.util.Try

/**
 * Runs a server and a headless Chromium. Each test gets a fresh browser context, i.e. a new session.
 *
 * Tests are canceled unless `-Dkreuzberg.e2e=true` is set, so that a plain `sbt test` does not download browsers.
 */
trait PlaywrightFixture extends TestSuiteMixin with BeforeAndAfterAll { this: TestSuite =>

  /** The server under test. */
  protected def createTarget(): Target

  private val enabled = sys.props.get("kreuzberg.e2e").contains("true")

  /** Created once; a failure is kept, so that cleanup does not run into it again. */
  private lazy val running    = Try {
    val target = createTarget()
    (target, TestServer.Running(target.miniServer))
  }
  private lazy val playwright = Playwright.create()
  private lazy val browser    = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))

  protected def baseUrl: String = running.get._2.baseUrl

  /** Run with a page in a fresh browser context. */
  protected def withPage[T](f: Page => T): T = {
    val context = browser.newContext()
    try {
      val page = context.newPage()
      page.onPageError(error => fail(s"Browser error: ${error}"))
      f(page)
    } finally {
      context.close()
    }
  }

  abstract override def withFixture(test: NoArgTest): Outcome = {
    if (enabled) super.withFixture(test)
    else org.scalatest.Canceled("Browser tests disabled, enable with -Dkreuzberg.e2e=true")
  }

  override protected def afterAll(): Unit = {
    if (enabled) {
      browser.close()
      playwright.close()
      running.foreach { case (target, server) =>
        server.close()
        target.close()
      }
    }
    super.afterAll()
  }
}

object PlaywrightFixture {

  /** The server under test; `close` releases resources besides the HTTP server. */
  case class Target(miniServer: MiniServer, close: () => Unit = () => ())
}
