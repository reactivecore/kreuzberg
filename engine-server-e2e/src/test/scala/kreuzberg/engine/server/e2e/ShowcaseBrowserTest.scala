package kreuzberg.engine.server.e2e

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitForSelectorState
import kreuzberg.engine.server.{ServerEngine, TestServer}
import kreuzberg.examples.showcase.{SsrMain, showcaseTranslations}
import kreuzberg.testcore.TestBase

import scala.annotation.experimental

/** Drives the showcase app in a browser, covering async flows (forwarding, lazy routes, API calls). */
@experimental
class ShowcaseBrowserTest extends TestBase with PlaywrightFixture {

  override protected def createEngine(): ServerEngine =
    SsrMain.engine(TestServer.config.copy(translations = Some(showcaseTranslations)))

  "the showcase" should "forward to the index page" in withPage { page =>
    page.navigate(s"${baseUrl}/xml")
    page.waitForSelector("h2:has-text('XML Example')")
    page.click("text=Forward")
    page.waitForURL(s"${baseUrl}/")
    page.waitForSelector(
      "h2:has-text('XML Example')",
      Page.WaitForSelectorOptions().setState(WaitForSelectorState.DETACHED)
    )
  }

  it should "show lazy loaded content after the Future resolved" in withPage { page =>
    page.navigate(s"${baseUrl}/")
    page.click("text=Lazy")
    page.waitForSelector("text=Loading")
    page.waitForSelector("text=This page was lazy loaded with this result")
    page.title() should include("Lazy")
  }

  it should "show lazy loaded content on a direct page load" in withPage { page =>
    page.navigate(s"${baseUrl}/lazy/123")
    page.waitForSelector("text=This page was lazy loaded with this result")
  }

  it should "append todo items with their wrapping HTML" in withPage { page =>
    page.navigate(s"${baseUrl}/todo")
    page.fill("input[name=name]", "milk")
    page.click("button:has-text('Add')")
    page.waitForSelector("ul > li > span:has-text('milk')")
  }

  it should "add a todo item via the API" in withPage { page =>
    page.navigate(s"${baseUrl}/todoapi")
    page.waitForSelector("h2:has-text('API Based TODO App')")
    page.fill("input[name=name]", "via api")
    page.click("button:has-text('Add')")
    page.waitForSelector("text=via api")
  }

  it should "validate while typing fast" in withPage { page =>
    page.navigate(s"${baseUrl}/form")
    val name = page.locator("input[name=name]")
    name.pressSequentially("ab")
    page.waitForSelector("text=Must contain at least 3 characters")
    name.pressSequentially("cd")
    page.waitForSelector("text=Must contain at least 3 characters", hidden)
    page.locator("input[name=age]").pressSequentially("x1")
    page.waitForSelector("text=Must be a number")
  }

  it should "read checkboxes, selects and numbers of generated forms" in withPage { page =>
    page.navigate(s"${baseUrl}/form2")
    page.fill("input[name=firstName]", "Alice")
    page.fill("input[name=lastName]", "Smith")
    page.fill("input[name=emailAddress]", "alice@example.com")
    page.fill("input[name=password]", "secret12")
    page.fill("input[name=passwordRepeat]", "secret12")
    page.fill("input[name=largeNumber]", "500")
    page.check("input[name=isAdult]")
    page.selectOption("select[name=favoriteColor]", "blue")
    page.click("button:has-text('Ok')")
    page.waitForSelector("text=Right(Register(Alice,Smith,alice@example.com,secret12,secret12,500,true,blue))")

    page.click("button:has-text('Reset')")
    page.waitForFunction("!document.querySelector('input[name=isAdult]').checked")
    page.inputValue("input[name=firstName]") shouldBe ""
    page.inputValue("select[name=favoriteColor]") shouldBe "red"
  }

  it should "load a lazy page when clicking twice" in withPage { page =>
    page.navigate(s"${baseUrl}/")
    page.click("text=Lazy")
    page.click("text=Lazy")
    page.waitForSelector("text=This page was lazy loaded with this result")
  }

  it should "switch the language with the language picker" in withPage { page =>
    page.navigate(s"${baseUrl}/")
    page.waitForSelector("h2:has-text('Hi There')")
    page.selectOption("select[aria-label='Language']", "de")
    page.waitForSelector("h2:has-text('Hallo')")
    page.getAttribute("html", "lang") shouldBe "de"
  }

  it should "render Scala XML pages (multi-line patches)" in withPage { page =>
    page.navigate(s"${baseUrl}/")
    page.click("text=XML")
    page.waitForSelector("h2:has-text('XML Example')")
    page.url() should endWith("/xml")
  }

  private val hidden = Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
}
