package kreuzberg.miniserver
import java.nio.file.{Files, Path, Paths}

class AssetCandidatePathTest extends TestBase {

  def locateDir(s: String): Path = {
    val cand1 = Paths.get(s)
    if (Files.isDirectory(cand1)) {
      return cand1
    }
    val cand2 = Paths.get("..", s)
    if (Files.isDirectory(cand2)) {
      return cand2
    }
    throw new IllegalStateException(s"Could not find directory, tried ${cand1}, ${cand2}")
  }

  "Resource" should "find a resource" in {
    val resource = AssetCandidatePath.Resource("test", "foobar")
    resource.locate("foobar/resource1.txt") shouldBe Some(Location.ResourcePath("test/resource1.txt"))
    resource.locate("foobar/resource2.txt") shouldBe None
    resource.locate("resource1.txt") shouldBe None
    resource.locate("other") shouldBe None

    val resource2 = AssetCandidatePath.Resource("assets/")
    resource2.locate("images/image1.png") shouldBe Some(Location.ResourcePath("assets/images/image1.png"))

  }

  "Directory" should "find a file" in {
    val baseDir = "miniserver-ziohttp/src/test/resources/test"
    val located = locateDir(baseDir)

    val dir = AssetCandidatePath.Directory(located.toString, "foobar")
    dir.locate("foobar/resource1.txt") shouldBe Some(Location.File(located.resolve("resource1.txt").toFile))
    dir.locate("resource1.txt") shouldBe None
    dir.locate("foobar/resource2.txt") shouldBe None
    dir.locate("foobar/../../scala/kreuzberg/miniserver-ziohttp/TestBase.scala") shouldBe None // escape attack
  }
}
