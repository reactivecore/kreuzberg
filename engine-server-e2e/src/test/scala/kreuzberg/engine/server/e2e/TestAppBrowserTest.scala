package kreuzberg.engine.server.e2e

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.Cookie
import kreuzberg.engine.server.{ServerEngine, TestApp, TestServer}
import kreuzberg.testcore.TestBase

import scala.jdk.CollectionConverters.*

/** Drives the engine-server test app in a browser. */
class TestAppBrowserTest extends TestBase with PlaywrightFixture {

  override protected def createEngine(): ServerEngine = TestApp.engine(TestServer.config)

  "the app" should "render the page on initial load" in withPage { page =>
    page.navigate(s"${baseUrl}/")
    page.waitForSelector("#page-home")
    page.title() shouldBe "Test - Home"
  }

  it should "navigate without reloading the page" in withPage { page =>
    page.navigate(s"${baseUrl}/")
    page.waitForSelector("#page-home")
    page.evaluate("window.marker = 42"): Unit
    page.click("text=counter")
    page.waitForSelector("#page-counter")
    page.url() should endWith("/counter")
    page.waitForFunction("document.title === 'Test - Counter'")
    page.evaluate("window.marker === 42") shouldBe java.lang.Boolean.TRUE
  }

  it should "process clicks" in withPage { page =>
    page.navigate(s"${baseUrl}/counter")
    page.textContent("#counter-value") shouldBe "0"
    page.click("#counter-inc")
    page.waitForFunction("document.querySelector('#counter-value').textContent === '1'")
    page.click("#counter-inc")
    page.waitForFunction("document.querySelector('#counter-value').textContent === '2'")
  }

  it should "read input values" in withPage { page =>
    page.navigate(s"${baseUrl}/form")
    page.fill("#form-input", "abcdef")
    page.waitForFunction("document.querySelector('#form-length').textContent === '6'")
  }

  it should "read and write input values" in withPage { page =>
    page.navigate(s"${baseUrl}/todo")
    page.fill("#todo-input", "buy milk")
    page.click("#todo-add")
    page.waitForSelector("#todo-list li:has-text('buy milk')")
    // Cleared by the server
    page.waitForFunction("document.querySelector('#todo-input').value === ''")
  }

  it should "show the loading state of lazy routes" in withPage { page =>
    page.navigate(s"${baseUrl}/")
    page.click("text=lazy")
    page.waitForSelector("text=Loading...")
    page.title() shouldBe "Test - Lazy..."
    page.waitForSelector("#page-lazy:has-text('Loaded x')")
    page.waitForFunction("document.title === 'Test - Lazy x'")
  }

  it should "pass keyboard data to the server" in withPage { page =>
    page.navigate(s"${baseUrl}/keys")
    page.locator("#key-input").pressSequentially("hello")
    page.locator("#key-input").press("Enter")
    page.waitForSelector("#entered:has-text('hello')")
  }

  it should "show results of async work arriving after the event" in withPage { page =>
    page.navigate(s"${baseUrl}/async")
    page.click("#async-start")
    page.waitForSelector("#async-result:has-text('running')")
    page.waitForSelector("#async-result:has-text('done')")
  }

  it should "keep the focus on re-rendered inputs" in withPage { page =>
    page.navigate(s"${baseUrl}/form")
    page.click("#form-input")
    page.keyboard().press("a")
    page.waitForSelector("#form-length:has-text('1')")
    page.evaluate("document.activeElement.id") shouldBe "form-input"
  }

  it should "reload an older tab of the session on its next event" in withPage { page =>
    page.navigate(s"${baseUrl}/counter")
    val second = page.context().newPage()
    second.navigate(s"${baseUrl}/counter")
    second.click("#counter-inc")
    second.waitForSelector("#counter-value:has-text('1')")
    // The first tab reloads and shows the shared state
    page.click("#counter-inc")
    page.waitForSelector("#counter-value:has-text('1')")
    page.click("#counter-inc")
    page.waitForSelector("#counter-value:has-text('2')")
  }

  it should "not let two tabs with timers take the session from each other" in withPage { page =>
    page.navigate(s"${baseUrl}/timer")
    page.evaluate("window.marker = 1")
    val second = page.context().newPage()
    second.navigate(s"${baseUrl}/timer")
    second.evaluate("window.marker = 2")
    page.waitForTimeout(1500)
    // Neither reloaded: the first tab stopped its timers, the second one keeps running
    page.evaluate("window.marker === 1") shouldBe java.lang.Boolean.TRUE
    second.evaluate("window.marker === 2") shouldBe java.lang.Boolean.TRUE
    val ticks  = second.textContent("#ticks").toInt
    second.waitForFunction(s"Number(document.querySelector('#ticks').textContent) > ${ticks}")
  }

  it should "show values the server rendered into a focused input" in withPage { page =>
    page.navigate(s"${baseUrl}/upper")
    page.locator("#upper-input").pressSequentially("abc")
    page.locator("#upper-input").press("Enter")
    page.waitForFunction("document.querySelector('#upper-input').value === 'ABC'")
    page.evaluate("document.activeElement.id") shouldBe "upper-input"
  }

  it should "run timers" in withPage { page =>
    page.navigate(s"${baseUrl}/timer")
    page.waitForFunction("Number(document.querySelector('#ticks').textContent) >= 3")
  }

  it should "keep the session on reload" in withPage { page =>
    page.navigate(s"${baseUrl}/todo")
    page.fill("#todo-input", "remember me")
    page.click("#todo-add")
    page.waitForSelector("#todo-list li:has-text('remember me')")
    sessionCookie(page) should not be empty

    page.reload()
    page.waitForSelector("#todo-list li:has-text('remember me')")
    page.title() shouldBe "Test - Todo"
  }

  it should "support back and forward" in withPage { page =>
    page.navigate(s"${baseUrl}/")
    page.click("text=counter")
    page.waitForSelector("#page-counter")
    page.click("text=form")
    page.waitForSelector("#page-form")
    page.goBack()
    page.waitForSelector("#page-counter")
    page.goForward()
    page.waitForSelector("#page-form")
  }

  it should "switch the locale" in withPage { page =>
    page.navigate(s"${baseUrl}/language")
    page.click("#language-inc")
    page.waitForSelector("#greeting:has-text('Hello 1')")
    page.click("#to-german")
    page.waitForSelector("#greeting:has-text('Hallo 1')")
    page.getAttribute("html", "lang") shouldBe "de"
  }

  it should "reload, if the session expired" in withPage { page =>
    page.navigate(s"${baseUrl}/counter")
    page.context().clearCookies()
    page
      .context()
      .addCookies(
        java.util.List.of(
          Cookie(ServerEngine.SessionCookieName, "expired").setUrl(baseUrl)
        )
      )
    page.click("#counter-inc")
    // The reload creates a new session
    page.waitForCondition(() => sessionCookie(page).exists(_ != "expired"))
    page.waitForSelector("#page-counter")
  }

  private def sessionCookie(page: Page): Option[String] = {
    page.context().cookies().asScala.find(_.name == ServerEngine.SessionCookieName).map(_.value)
  }
}
