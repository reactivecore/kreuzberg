package kreuzberg.engine.server

import kreuzberg.testcore.TestBase

class PageTest extends TestBase {

  private def titles(n: Int): Seq[ClientProtocol.Op] = (1 to n).map(i => ClientProtocol.Op.Title(s"t${i}"))

  "Page" should "number operations and return those after a position" in {
    val page = Page()
    page.append(titles(3))
    page.lastSeq shouldBe 3
    page.after(1).get.map(_.contains("\"seq\":")) shouldBe Seq(true, true)
    page.after(1).get.head should include("\"t2\"")
    page.after(3) shouldBe Some(Nil)
    page.hasAfter(2) shouldBe true
    page.hasAfter(3) shouldBe false
  }

  it should "drop confirmed operations" in {
    val page = Page()
    page.append(titles(3))
    page.confirm(2)
    page.after(2).get.size shouldBe 1
    // Not available anymore, the client has to reload
    page.after(0) shouldBe None
  }

  it should "reject unknown positions" in {
    val page = Page()
    page.append(titles(2))
    page.after(5) shouldBe None
  }

  it should "limit its size" in {
    val page = Page()
    val big  = "x" * (Page.MaxBufferedChars / 3).toInt
    page.append(Seq.fill(4)(ClientProtocol.Op.Title(big)))
    page.lastSeq shouldBe 4
    page.after(0) shouldBe None
    page.after(2).get.size shouldBe 2
  }
}
