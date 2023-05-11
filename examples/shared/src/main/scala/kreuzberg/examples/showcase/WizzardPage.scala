package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.imperative.{AssemblyContext, ImperativeComponentBase}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class WizzardablePage(
    component: Component
) {
  def assemble(name: String): TreeNodeResult = Assembler.assembleNamedChild(name, component)
}

case class SimpleText(text: String) extends ImperativeComponentBase {
  override def assemble(implicit c: AssemblyContext): Assembly[Unit] = {
    div(text)
  }
}

case class Wizzard(
    pages: Seq[WizzardablePage]
) extends ImperativeComponentBase {
  override def assemble(implicit c: AssemblyContext): Assembly[Unit] = {
    val pageModel   = model("page", 0)
    val currentPage = subscribe(pageModel)
    val selected    = pages(currentPage)
    val rendered    = c.transform(selected.assemble(currentPage.toString))
    val prevButton  = child("prev", Button("previous"))
    val nextButton  = child("next", Button("next"))

    val bindings = Vector(
      from(prevButton)(_.clicked).changeModelDirect(pageModel)(x => Math.max(0, x - 1)),
      from(nextButton)(_.clicked).changeModelDirect(pageModel)(x => Math.min(pages.size - 1, x + 1))
    )

    Assembly(
      div(rendered.wrap, prevButton.wrap, nextButton.wrap),
      bindings
    )
  }
}

object WizzardPage extends ImperativeComponentBase {
  override def assemble(implicit c: AssemblyContext): Assembly[Unit] = {
    val page1        = WizzardablePage(SimpleText("This is page 1"))
    val page2        = WizzardablePage(SimpleText("This is page 2"))
    val page3        = WizzardablePage(SimpleText("This is page 3"))
    val wizzard      = Wizzard(
      Seq(
        page1,
        page2,
        page3
      )
    )
    val wizzardChild = child("wizzard", wizzard)
    div(wizzardChild.wrap)
  }
}
