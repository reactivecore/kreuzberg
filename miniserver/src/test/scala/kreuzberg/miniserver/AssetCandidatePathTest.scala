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

  it should "load npm webjars" in {
    val candidatePath = AssetCandidatePath.Webjar("foo/")
    val found         = candidatePath.locate("foo/hash-wasm/dist/index.umd.js").value
    found.load().readAllBytes().size shouldBe >=(10)

    // The same instance still serves classical webjars
    candidatePath.locate("foo/jquery/jquery.js") shouldBe defined

    candidatePath.locate("foo/hash-wasm/dist/missing.js") shouldBe empty
    candidatePath.locate("hash-wasm/dist/index.umd.js") shouldBe empty
    candidatePath.locate("foo/other/index.umd.js") shouldBe empty
    candidatePath.locate("foo/hash-wasm") shouldBe empty
  }

  it should "respect the configured groups" in {
    val classical = AssetCandidatePath.Webjar("foo/", Seq("org.webjars"))
    classical.locate("foo/jquery/jquery.js") shouldBe defined
    classical.locate("foo/hash-wasm/dist/index.umd.js") shouldBe empty

    val npm = AssetCandidatePath.Webjar("foo/", Seq("org.webjars.npm"))
    npm.locate("foo/hash-wasm/dist/index.umd.js") shouldBe defined
    npm.locate("foo/jquery/jquery.js") shouldBe empty
  }

  "Location" should "update the cached hash of changed files" in {
    val production = Some(DeploymentType.Production)
    val file       = Files.createTempFile("kreuzberg_test", ".txt")
    file.toFile.deleteOnExit()
    Files.writeString(file, "Hello")
    val location   = Location.File(file.toFile)
    val first      = location.hashSha256(production)
    first shouldBe "185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969" // sha256("Hello")
    location.hashSha256(production) shouldBe first

    Files.writeString(file, "Hello World")
    location.hashSha256(production) shouldBe "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e"
  }

  it should "not cache hashes in debug mode" in {
    val debug    = Some(DeploymentType.Debug)
    val file     = Files.createTempFile("kreuzberg_test", ".txt")
    file.toFile.deleteOnExit()
    Files.writeString(file, "Hello")
    val location = Location.File(file.toFile)
    location.hashSha256(debug) shouldBe "185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969"

    // Same size, likely within the same modification time tick
    Files.writeString(file, "Hallo")
    location.hashSha256(debug) shouldBe "753692ec36adb4c794c973945eb2a99c1649703ea6f76bf259abb4fb838e013e"
  }

  it should "know the size of files and resources" in {
    val fromDirectory = AssetCandidatePath.Resource("test_assets/").locate("asset1.txt").value
    fromDirectory.size() shouldBe 15

    val fromJar = AssetCandidatePath.Webjar().locate("jquery/jquery.js").value
    fromJar.size() shouldBe fromJar.load().readAllBytes().length
  }

  it should "know about Brotli variants" in {
    val plain = AssetCandidatePath.Resource("test_assets/").locate("asset1.txt").value
    plain.brotliVariant() shouldBe empty

    val compressed = AssetCandidatePath.Resource("test_assets/").locate("compressed.txt").value
    compressed.brotliVariant() shouldBe AssetCandidatePath.Resource("test_assets/").locate("compressed.txt.br")
  }

  it should "be the same for other spellings of a directory path" in {
    val dir       = Files.createTempDirectory("kreuzberg_test")
    Files.writeString(dir.resolve("test.txt"), "Hello")
    val candidate = AssetCandidatePath.Directory(dir.toString)
    candidate.locate("test.txt") shouldBe defined
    candidate.locate("./test.txt") shouldBe candidate.locate("test.txt")
    candidate.locate("././test.txt") shouldBe candidate.locate("test.txt")
  }

  it should "not locate directories" in {
    val dir       = Files.createTempDirectory("kreuzberg_test")
    Files.createDirectory(dir.resolve("sub"))
    val candidate = AssetCandidatePath.Directory(dir.toString)
    candidate.locate("sub") shouldBe empty
    candidate.locate("") shouldBe empty
  }
}
