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
