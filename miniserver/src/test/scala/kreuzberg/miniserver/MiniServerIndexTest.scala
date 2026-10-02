package kreuzberg.miniserver

import kreuzberg.testcore.TestBase

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class MiniServerIndexTest extends TestBase {

  trait Env {
    val dir: Path = Files.createTempDirectory("miniserver-index")

    def writeMainJs(content: String): Unit = {
      Files.write(dir.resolve("main.js"), content.getBytes(StandardCharsets.UTF_8))
    }

    def server(deploymentType: DeploymentType): MiniServer = {
      val deployment = DeploymentConfig(
        assetPaths = AssetPaths(Seq(AssetCandidatePath(dir.toString))),
        deploymentType = deploymentType
      )
      new MiniServer(MiniServerConfig(deployment))
    }

    def mainJsUrl(server: MiniServer): String = {
      val html = server.makeIndexHtml(Nil, Nil)
      """src="(/assets/main\.js[^"]*)"""".r.findFirstMatchIn(html).get.group(1)
    }

    writeMainJs("console.log(1)")
  }

  it should "detect lang in root attributes" in {
    import kreuzberg.scalatags.all.*
    Index.hasLangAttribute(Seq(lang := "en")) shouldBe true
    Index.hasLangAttribute(Seq(attr("data-x") := "1", cls := "dark")) shouldBe false
    Index.hasLangAttribute(Nil) shouldBe false
  }

  it should "still work with a conflicting lang (only logging an error)" in {
    import kreuzberg.scalatags.all.*
    val server = new MiniServer(
      MiniServerConfig(
        DeploymentConfig(htmlRootAttributes = Seq(lang := "en")),
        translations = Some(TranslationConfig(Seq("test_i18n/test.default.rctr", "test_i18n/test.de.rctr")))
      )
    )
    server.makeIndexHtml(Nil, Nil) should include("<html lang=\"en en\">")
  }

  it should "hash assets once in production" in new Env {
    val s      = server(DeploymentType.Production)
    val before = mainJsUrl(s)
    before should startWith("/assets/main.js?hash=")
    writeMainJs("console.log(2)")
    mainJsUrl(s) shouldBe before
  }

  it should "hash assets on each request in debug mode" in new Env {
    val s      = server(DeploymentType.Debug)
    val before = mainJsUrl(s)
    writeMainJs("console.log(2)")
    mainJsUrl(s) should not be before
  }
}
