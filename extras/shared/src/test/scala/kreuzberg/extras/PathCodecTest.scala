package kreuzberg.extras

import kreuzberg.testcore.TestBase

import java.util.UUID

class PathCodecTest extends TestBase {

  "const" should "work" in {
    val codec = PathCodec.const("/todo")
    codec.handles(UrlResource("/todo")) shouldBe true
    codec.handles(UrlResource("/other")) shouldBe false

    codec.handlesPrefix(UrlResource("/todo/baz")) shouldBe Some(UrlResource("/baz"))

    codec.handles(UrlResource("/todo/baz")) shouldBe false
    codec.decode(UrlResource("/other")).left.value shouldBe an[Error.BadPathError]
    codec.decode(UrlResource("/todo")).value shouldBe ()
  }

  "query decoding" should "work" in {
    val codec = PathCodec.recursive("/foo/bar").query[String]("userId").query[Int]("token")
    codec.encode(("user1", 123)).str shouldBe "/foo/bar?userId=user1&token=123"
    codec.handles(UrlResource("/somethingelse")) shouldBe false
    codec.handles(UrlResource("/foo/bar")) shouldBe true
    codec.decode(UrlResource("/foo/bar")).left.value shouldBe an[Error.MissingQueryParameter]
    codec.handles(UrlResource("/foo/bar?userId=user1&token=123")) shouldBe true
    codec.decode(UrlResource("/foo/bar")).isLeft shouldBe true
    codec.decode(UrlResource("/foo/bar?userId=user1&token=123")) shouldBe Right(("user1", 123))
    codec.decode(UrlResource("/foo/bar?token=123&userId=user1")) shouldBe Right(("user1", 123))
    codec.decode(UrlResource("/foo/bar?userId=token=123")).isLeft shouldBe true
  }

  "RecursivePath" should "work" in {
    val prefix = PathCodec.recursive("/foo/bar")
    prefix.encode(EmptyTuple) shouldBe UrlResource("/foo/bar")
    prefix.decode(UrlResource("/foo/bar")) shouldBe Right(EmptyTuple)
    prefix.handles(UrlResource("/foo/bar")) shouldBe true
    prefix.handles(UrlResource("/foo/bar2")) shouldBe false

    prefix.decodePrefix(UrlResource("/foo/bar/blub")) shouldBe Right(EmptyTuple -> UrlResource("/blub"))

    val uuid    = UUID.fromString("b57460d4-37c3-4c0b-8c3e-424fd8090267")
    val child   = prefix.string.boolean.uuid.fixed("bub")
    val encoded = UrlResource(s"/foo/bar/Hallo/true/${uuid}/bub")
    child.encode("Hallo", true, uuid) shouldBe encoded
    child.decode(encoded) shouldBe Right(("Hallo", true, uuid))
    child.handles(encoded) shouldBe true

    child.decodePrefix(UrlResource(s"/foo/bar/Hallo/true/${uuid}/bub/baz")) shouldBe Right(
      ("Hallo", true, uuid) -> UrlResource("/baz")
    )

    val bad1 = UrlResource(s"/foo/bar/Hallo/true/${uuid}/bub1")
    val bad2 = UrlResource(s"/foo/bar2/Hallo/true/${uuid}/bub")
    val bad3 = UrlResource("/foo/bar/Hallo/true/no-uuid/bub")
    child.handles(bad1) shouldBe false
    child.decode(bad1) shouldBe Left(Error.BadPathElementError("bub1", "bub"))

    child.handles(bad2) shouldBe false
    child.decode(bad2).left.value shouldBe an[Error.BadPathError]

    child.handles(bad3) shouldBe true
    child.decode(bad3).left.value shouldBe an[Error.DecodingError]

    val single = prefix.string.one[String]
    single.encode("bim") shouldBe UrlResource("/foo/bar/bim")
    single.decode(UrlResource("/foo/bar/buz")) shouldBe Right("buz")
    single.decodePrefix(UrlResource("/foo/bar/buz/biz")) shouldBe Right("buz", UrlResource("/biz"))
  }

}
