package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.imperative.{AssemblyContext, ImperativeComponentBase}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class WizzardablePage[T](
    component: T
)(implicit assembler: Assembler[T]) {
  def assemble(name: String): NodeResult[T] = assembler.assembleNamedChild(name, component)
}

case class SimpleText(text: String) extends ImperativeComponentBase {
  override def assemble(implicit c: AssemblyContext): Assembly = {
    div(text)
  }
}

case class Wizzard(
    pages: Seq[WizzardablePage[_]]
) extends ImperativeComponentBase {
  override def assemble(implicit c: AssemblyContext): Assembly = {
    val pageModel   = model("page", 0)
    val currentPage = subscribe(pageModel)
    val selected    = pages(currentPage)
    val rendered    = c.transform(selected.assemble(currentPage.toString))
    val prevButton  = child("prev", Button("previous"))
    val nextButton  = child("next", Button("next"))

    val bindings = Vector(
      from(prevButton)(_.clicked).toModelChange(pageModel)(x => Math.max(0, x - 1)),
      from(nextButton)(_.clicked).toModelChange(pageModel)(x => Math.min(pages.size - 1, x + 1))
    )

    Assembly(
      div,
      Vector(rendered, prevButton, nextButton),
      bindings
    )
  }
}

object WizzardPage extends ImperativeComponentBase {
  override def assemble(implicit c: AssemblyContext): Assembly = {
    val page1   = WizzardablePage(SimpleText("This is page 1"))
    val page2   = WizzardablePage(SimpleText("This is page 2"))
    val page3   = WizzardablePage(SimpleText("This is page 3"))
    val wizzard = Wizzard(
      Seq(
        page1,
        page2,
        page3
      )
    )
    Assembly(
      div,
      Vector(child("wizzard", wizzard))
    )
  }
}
