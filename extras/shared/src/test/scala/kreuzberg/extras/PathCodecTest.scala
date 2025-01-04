package kreuzberg.extras

import kreuzberg.testcore.TestBase

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

}
