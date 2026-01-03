package kreuzberg.miniserver

import kreuzberg.testcore.TestBase

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class AssetCandidatePathTest extends TestBase {

  "Resource" should "load resources" in {
    val candidatePath = AssetCandidatePath.Resource("test_resource/sub1/", "foo/")

    val bytes = candidatePath.locate("foo/Test.txt").value.load().readAllBytes()
    new String(bytes, StandardCharsets.UTF_8).trim shouldBe "Hello Testcase!"

    candidatePath.locate("foo/not_found") shouldBe empty
    candidatePath.locate("foo/../sub2/Hidden.txt") shouldBe empty

    val candidatePath2 = AssetCandidatePath.Resource("test_resource", "")
    candidatePath2.locate("sub1/Test.txt")
    new String(bytes, StandardCharsets.UTF_8).trim shouldBe "Hello Testcase!"

    candidatePath2.locate("../sub1/Test.txt") shouldBe empty
  }

  "Directory" should "load files" in {
    val dir = Files.createTempDirectory("kreuzberg_test")
    Files.createDirectory(dir.resolve("sub1"))
    Files.createDirectory(dir.resolve("sub2"))
    Files.writeString(dir.resolve("sub1/test.txt"), "Hello World")
    Files.writeString(dir.resolve("sub2/test.txt"), "Hidden")

    val candidatePath = AssetCandidatePath.Directory(dir.resolve("sub1").toString, "foo/")
    new String(
      candidatePath.locate("foo/test.txt").value.load().readAllBytes(),
      StandardCharsets.UTF_8
    ) shouldBe "Hello World"
    candidatePath.locate("foo/unknown") shouldBe empty
    candidatePath.locate("test.txt") shouldBe empty

    candidatePath.locate("foo/../sub2/test.txt") shouldBe empty
  }

  "Webjar" should "load webjars" in {
    val candidatePath = AssetCandidatePath.Webjar("foo/")
    val found         = candidatePath.locate("foo/jquery/jquery.js").value
    found.load().readAllBytes().size shouldBe >=(10)

    candidatePath.locate("foo/jquery/missing.js") shouldBe empty
    candidatePath.locate("jquery/jquery.js") shouldBe empty
    candidatePath.locate("foo/other/jquery.js") shouldBe empty
    candidatePath.locate("jquery") shouldBe empty
    candidatePath.locate("foo/jquery") shouldBe empty
  }
}
