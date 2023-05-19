package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class WizzardablePage(
    component: Component
)

case class SimpleText(text: String) extends SimpleComponentBase {

  override def assemble(implicit c: SimpleContext): Html = {
    div(text)
  }
}

case class Wizzard(
    pages: Seq[WizzardablePage]
) extends SimpleComponentBase {

  val pageModel = Model.create(0)

  override def assemble(implicit c: SimpleContext): Html = {
    val currentPage = subscribe(pageModel)
    val selected    = pages(currentPage)
    val rendered    = selected.component
    val prevButton  = Button("previous")
    val nextButton  = Button("next")

    add(
      from(prevButton.clicked).changeModelDirect(pageModel)(x => Math.max(0, x - 1)),
      from(nextButton.clicked).changeModelDirect(pageModel)(x => Math.min(pages.size - 1, x + 1))
    )

    div(rendered.wrap, prevButton.wrap, nextButton.wrap)
  }
}

object WizzardPage extends SimpleComponentBase {

  override def assemble(implicit c: SimpleContext): Html = {
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
    div(wizzard.wrap)
  }
}
