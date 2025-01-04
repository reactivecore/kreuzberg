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
}
