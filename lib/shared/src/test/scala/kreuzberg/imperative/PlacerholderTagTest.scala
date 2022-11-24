package kreuzberg.imperative

import kreuzberg.TestBase
import kreuzberg._
import scalatags.Text.all._

class PlacerholderTagTest extends TestBase {

  case class DummyComponent(i: Int) extends ComponentBase {
    def assemble: AssemblyResult = {
      div(s"Hello World ${i}")
    }
  }

  def node(i: Int) = ComponentNode(
    ComponentId(i),
    DummyComponent(i),
    DummyComponent(i).assemble(AssemblyState())._2,
    implicitly[Assembler[DummyComponent]]
  )

  "collectFrom" should "work" in {
    val p0          = PlaceholderTag(node(0))
    val p1          = PlaceholderTag(node(1))
    val p2          = PlaceholderTag(node(2))
    val p3          = PlaceholderTag(node(2))
    val placeholder = PlaceholderTag(node(3))
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
    PlaceholderTag.collectFrom(html) shouldBe Vector(p0, p1, p2, p3)
  }

}
