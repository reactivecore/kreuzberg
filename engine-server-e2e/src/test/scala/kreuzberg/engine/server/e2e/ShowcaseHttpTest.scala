package kreuzberg.engine.server.e2e

import kreuzberg.engine.server.TestServer
import kreuzberg.examples.showcase.{SsrMain, showcaseTranslations}
import kreuzberg.testcore.TestBase
import org.scalatest.BeforeAndAfterAll

import scala.annotation.experimental

/** Runs the showcase without a browser, checking the HTTP responses. */
@experimental
class ShowcaseHttpTest extends TestBase with BeforeAndAfterAll {

  private lazy val engine = SsrMain.engine(TestServer.config.copy(translations = Some(showcaseTranslations)))
  private lazy val server = TestServer.Running(engine.miniServer)

  override protected def afterAll(): Unit = {
    server.close()
    engine.close()
  }

  "the showcase" should "render all pages of the menu" in server.withClient { client =>
    val index = client.get("/")
    index.status shouldBe 200
    index.body should include("<h2>Hi There</h2>")
    val links = """<a href="(/[^"]*)"""".r.findAllMatchIn(index.body).map(_.group(1)).toSet
    links should contain allOf ("/", "/todo", "/xml", "/form")
    links.foreach { link =>
      withClue(link) {
        // The menu links to the not found page on purpose
        client.get(link).status shouldBe (if (link == "/notfound") 404 else 200)
      }
    }
  }

  it should "render translated pages" in server.withClient { client =>
    client.get("/", "Accept-Language" -> "fr").body should include("""<html lang="fr">""")
    client.get("/", "Cookie" -> "lang=de").body should include("<h2>Hallo</h2>")
  }

  it should "patch multi-line XML pages, one operation per line" in server.withClient { client =>
    val index    = client.get("/").body
    val linkId   = """<a href="/xml" data-id="(\d+)"""".r.findFirstMatchIn(index).get.group(1)
    val response = client.event(listeners = Seq(s"e:${linkId}:click"))
    response.status shouldBe 200
    response.body should include("XML Example")
    response.body.linesIterator.foreach { line =>
      io.circe.parser.parse(line).isRight shouldBe true
    }
  }

  it should "render lazy routes completely on a direct page load" in server.withClient { client =>
    client.get("/lazy/123").body should include("This page was lazy loaded with this result")
  }
}
