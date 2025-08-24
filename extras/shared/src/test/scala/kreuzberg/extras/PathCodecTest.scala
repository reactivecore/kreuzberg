package kreuzberg.extras

import kreuzberg.testcore.TestBase

import java.util.UUID

class PathCodecTest extends TestBase {

  "constWithQueryParams" should "work" in {
    val codec = PathCodec.constWithQueryParams("/foo/bar", "userId", "token")
    codec.encode(Seq("user1", "123")).str shouldBe "/foo/bar?userId=user1&token=123"
    codec.handles(UrlResource("/somethingelse")) shouldBe false
    codec.handles(UrlResource("/foo/bar")) shouldBe false
    codec.handles(UrlResource("/foo/bar?userId=user1&token=123")) shouldBe true
    codec.decode(UrlResource("/foo/bar")) shouldBe None
    codec.decode(UrlResource("/foo/bar?userId=user1&token=123")) shouldBe Some(Seq("user1", "123"))
    codec.decode(UrlResource("/foo/bar?token=123&userId=user1")) shouldBe Some(Seq("user1", "123"))
    codec.decode(UrlResource("/foo/bar?userId=token=123")) shouldBe None
  }

  "RecursivePath" should "work" in {
    val prefix = PathCodec.recursive("/foo/bar")
    prefix.encode(EmptyTuple) shouldBe UrlResource("/foo/bar")
    prefix.decode(UrlResource("/foo/bar")) shouldBe Some(EmptyTuple)
    prefix.handles(UrlResource("/foo/bar")) shouldBe true
    prefix.handles(UrlResource("/foo/bar2")) shouldBe false

    val uuid    = UUID.fromString("b57460d4-37c3-4c0b-8c3e-424fd8090267")
    val child   = prefix.string.boolean.uuid.fixed("bub")
    val encoded = UrlResource(s"/foo/bar/Hallo/true/${uuid}/bub")
    child.encode("Hallo", true, uuid) shouldBe encoded
    child.decode(encoded) shouldBe Some(("Hallo", true, uuid))
    child.handles(encoded) shouldBe true

    val bad1 = UrlResource(s"/foo/bar/Hallo/true/${uuid}/bub1")
    val bad2 = UrlResource(s"/foo/bar2/Hallo/true/${uuid}/bub")
    val bad3 = UrlResource(s"/foo/bar/Hallo/true/no-uuid/bub")
    child.handles(bad1) shouldBe false
    child.decode(bad1) shouldBe None

    child.handles(bad2) shouldBe false
    child.decode(bad2) shouldBe None

    child.handles(bad3) shouldBe false
    child.decode(bad3) shouldBe None
  }

}
