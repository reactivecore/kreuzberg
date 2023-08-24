package kreuzberg.engine.common

import kreuzberg.*

class TreeNodeTest extends TestBase {

  case class Inner(title: Model[String]) extends SimpleComponentBase {
    def assemble(implicit c: SimpleContext): Html = {
      subscribe(title)
      SimpleHtml("div", children = Vector(SimpleHtmlNode.Text(s"Hi ${title}")))
    }
  }

  case class Outer() extends SimpleComponentBase {
    val a           = Model.create("Child1")
    val b           = Model.create("Child2")
    val headerModel = Model.create("rootTitle")
    val footerModel = Model.create("rootFooter")

    val child1 = Inner(a)
    val child2 = Inner(b)

    def assemble(implicit c: SimpleContext): Html = {
      val header = subscribe(headerModel)
      val footer = subscribe(footerModel)
      SimpleHtml(
        "div",
        children = Vector(
          SimpleHtmlNode.Text(s"Header: ${header}"),
          SimpleHtmlNode.Text("First: "),
          SimpleHtmlNode.EmbeddedComponent(child1),
          SimpleHtmlNode.Text(" Second: "),
          SimpleHtmlNode.EmbeddedComponent(child2),
          SimpleHtmlNode.Text(s"Footer: ${footer}")
        )
      )
    }
  }

  it should "correctly traverse ids" in {
    val initialized = IdentifierFactory.withFresh {
      Outer()
    }

    val tree = Assembler.singleTree(() => initialized)
    tree.allReferencedComponentIds.toVector shouldBe Vector(
      initialized.id,
      initialized.child1.id,
      initialized.child2.id
    )
    tree.allSubscriptions.toVector shouldBe Vector(
      initialized.headerModel.id -> initialized.id,
      initialized.footerModel.id -> initialized.id,
      initialized.a.id           -> initialized.child1.id,
      initialized.b.id           -> initialized.child2.id
    )
  }
}
