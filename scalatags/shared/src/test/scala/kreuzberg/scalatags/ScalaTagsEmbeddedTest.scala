package kreuzberg.scalatags

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.testcore.TestBase

class ScalaTagsEmbeddedTest extends TestBase {
  case class DummyComponent(i: Int) extends ComponentBase {
    def assemble(using context: AssemblerContext): Assembly = {
      div(s"Hello World ${i}")
    }
  }

  def node(i: Int) = DummyComponent(i)

  "collectFrom" should "work" in {
    val p0   = node(0).wrap
    val p1   = node(1).wrap
    val p2   = node(2).wrap
    val p3   = node(2).wrap
    val html = div(
      div(
        "foo"
      ),
      div(
        ("bar"),
        div(
          p0,
          p1,
          Seq(p2, p3)
        )
      )
    )
    ScalaTagsEmbedding.collectFrom(html) shouldBe Vector(p0, p1, p2, p3)
  }
}
