package kreuzberg.extras

import kreuzberg.testcore.TestBase

class UrlResourceTest extends TestBase {
  "encodeWithArgs" should "work" in {
    UrlResource.encodeWithArgs("foo/bar", Nil).str shouldBe "foo/bar"
    UrlResource.encodeWithArgs("foo/bar", Seq("x" -> "1", "y" -> "2")).str shouldBe "foo/bar?x=1&y=2"

    UrlResource.encodeWithArgs("baz", Seq("123=58&" -> "&!1")).str should (
      be("baz?123%3D58%26=%26!1") or be("baz?123%3D58%26=%26%211")
    )

    UrlResource.encodeWithArgs("baz", Seq("x" -> "y"), "bub!").str should (be("baz?x=y#bub!") or be("baz?x=y#bub%21"))
    UrlResource.encodeWithArgs("baz", fragment = "bub!").str should (be("baz#bub!") or be("baz#bub%21"))
  }

  "fullDecode" should "work" in {

    UrlResource("foo").path shouldBe "foo"

    val complicated = UrlResource("foo/bar?x=1&y=2")
    complicated.path shouldBe "foo/bar"
    complicated.queryArgs shouldBe Map("x" -> "1", "y" -> "2")
    complicated.fragment shouldBe ""

    val withEscaping = UrlResource("baz?123%3D58%26=%26!1#bim%3D")
    withEscaping.queryArgs shouldBe Map("123=58&" -> "&!1")
    withEscaping.fragment shouldBe "bim="

    val withEscaping2 = UrlResource("baz?x=y#bub%21")
    withEscaping2.queryArgs shouldBe Map("x" -> "y")
    withEscaping2.fragment shouldBe "bub!"
  }

  "dropSubPath" should "work" in {
    UrlResource("").dropSubPath shouldBe None
    UrlResource("/").dropSubPath shouldBe Some("", UrlResource(""))
    UrlResource("/hallo").dropSubPath shouldBe Some("hallo", UrlResource(""))
    UrlResource("/hallo?bub=bab#xyz").dropSubPath shouldBe Some("hallo", UrlResource("?bub=bab#xyz"))
    UrlResource("/hallo/welt").dropSubPath shouldBe Some("welt", UrlResource("/hallo"))
    UrlResource("/hallo/welt/du").dropSubPath shouldBe Some("du", UrlResource("/hallo/welt"))
    UrlResource("/hallo/welt?arg1=foo&arg2=bar").dropSubPath shouldBe Some(
      "welt",
      UrlResource("/hallo?arg1=foo&arg2=bar")
    )
  }

  "subPath" should "work" in {
    UrlResource("").subPath("foo") shouldBe UrlResource("/foo")
    UrlResource("/foo").subPath("bar") shouldBe UrlResource("/foo/bar")
    UrlResource("/foo?a=b#123").subPath("bar") shouldBe UrlResource("/foo/bar?a=b#123")
  }
}
