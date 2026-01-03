package kreuzberg.extras

import kreuzberg.testcore.TestBase

class UrlResourceTest extends TestBase {
  "apply" should "silently convert to absolute paths" in {
    UrlResource("foo///bar") shouldBe UrlResource("/foo/bar")
    UrlResource("foo///bar").toString shouldBe "/foo/bar"
  }

  "str" should "work" in {
    UrlResource(UrlPath(List("foo", "bar"))).str shouldBe "/foo/bar"
    UrlResource(UrlPath(List("foo", "bar")), Seq("x" -> "1", "y" -> "2")).str shouldBe "/foo/bar?x=1&y=2"
    UrlResource("/baz", Seq("123=58&" -> "&!1")).str should (be("/baz?123%3D58%26=%26!1") or be(
      "/baz?123%3D58%26=%26%211"
    ))
    UrlResource("/baz", Seq("x" -> "y"), "bub!").str should (be("/baz?x=y#bub!") or be("/baz?x=y#bub%21"))
    UrlResource("/baz", fragment = "bub!").str should (be("/baz#bub!") or be("/baz#bub%21"))
  }

  "fullDecode" should "work" in {

    UrlResource("foo").path shouldBe UrlPath.decode("foo")

    val complicated = UrlResource("foo/bar?x=1&y=2")
    complicated.path shouldBe UrlPath.decode("foo/bar")
    complicated.queryMap shouldBe Map("x" -> "1", "y" -> "2")
    complicated.fragment shouldBe ""

    val withEscaping = UrlResource("/baz?123%3D58%26=%26!1#bim%3D")
    withEscaping.queryMap shouldBe Map("123=58&" -> "&!1")
    withEscaping.fragment shouldBe "bim="

    withEscaping.str should (be("/baz?123%3D58%26=%26!1#bim%3D") or be("/baz?123%3D58%26=%26%211#bim%3D"))

    val withEscaping2 = UrlResource("baz?x=y#bub%21")
    withEscaping2.queryMap shouldBe Map("x" -> "y")
    withEscaping2.fragment shouldBe "bub!"

    val withEscaping3 = UrlResource("foo%20bar/biz")
    withEscaping3.path.path shouldBe List("foo bar", "biz")
    withEscaping3.str should be("/foo%20bar/biz")
  }

  "dropFirstPathPart" should "work" in {
    UrlResource("").dropFirstPathPart shouldBe None
    UrlResource("/bla").dropFirstPathPart shouldBe Some("bla", UrlResource())
    UrlResource("/bla/blub").dropFirstPathPart shouldBe Some("bla", UrlResource("/blub"))
    UrlResource("/bla/blub/blib").dropFirstPathPart shouldBe Some("bla", UrlResource("/blub/blib"))
  }

  "prependPathPart" should "work" in {
    UrlResource("").prependPathPart("blub") shouldBe UrlResource("/blub")
    UrlResource("/").prependPathPart("blub") shouldBe UrlResource("/blub")
    UrlResource("/bib/baz").prependPathPart("blub") shouldBe UrlResource("/blub/bib/baz")
  }
}
