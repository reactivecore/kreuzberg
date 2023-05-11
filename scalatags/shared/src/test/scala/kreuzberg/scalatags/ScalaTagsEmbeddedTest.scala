package kreuzberg.scalatags

import kreuzberg.{Assembler, AssemblyState, ComponentBase, ComponentId, ComponentNode, AssemblyResult}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

class ScalaTagsEmbeddedTest extends TestBase {
  case class DummyComponent(i: Int) extends ComponentBase {
    def assemble: AssemblyResult[Unit] = {
      div(s"Hello World ${i}")
    }
  }

  def node(i: Int) = ComponentNode.build(
    ComponentId(i),
    DummyComponent(i),
    DummyComponent(i).assemble(AssemblyState())._2
  )

  "collectFrom" should "work" in {
    val p0          = node(0).wrap
    val p1          = node(1).wrap
    val p2          = node(2).wrap
    val p3          = node(2).wrap
    val html        = div(
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
