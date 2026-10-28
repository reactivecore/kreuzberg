package kreuzberg.engine.server.e2e

import com.microsoft.playwright.{BrowserType, Page, Playwright}
import kreuzberg.engine.server.{ServerEngine, TestServer}
import org.scalatest.{BeforeAndAfterAll, Outcome, TestSuite, TestSuiteMixin}

/**
 * Runs a server and a headless Chromium. Each test gets a fresh browser context, i.e. a new session.
 *
 * Tests are canceled unless `-Dkreuzberg.e2e=true` is set, so that a plain `sbt test` does not download browsers.
 */
trait PlaywrightFixture extends TestSuiteMixin with BeforeAndAfterAll { this: TestSuite =>

  /** The engine under test. */
  protected def createEngine(): ServerEngine

  private val enabled = sys.props.get("kreuzberg.e2e").contains("true")

  private lazy val engine     = createEngine()
  private lazy val server     = TestServer.Running(engine.miniServer)
  private lazy val playwright = Playwright.create()
  private lazy val browser    = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))

  protected def baseUrl: String = server.baseUrl

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
      server.close()
      engine.close()
    }
    super.afterAll()
  }
}
