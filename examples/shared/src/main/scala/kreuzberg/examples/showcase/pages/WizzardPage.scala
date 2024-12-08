package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.examples.showcase.components.Button
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.extras.SimpleRouted

case class SimpleText(text: String) extends SimpleComponentBase {

  override def assemble(implicit c: SimpleContext): Html = {
    div(text)
  }
}

case class Wizzard(
    pages: Seq[Component]
) extends SimpleComponentBase {

  val pageModel = Model.create(0)

  override def assemble(implicit c: SimpleContext): Html = {
    val currentPage = subscribe(pageModel)
    val selected    = pages(currentPage)
    val prevButton  = Button("previous")
    val nextButton  = Button("next")

    add(
      prevButton.onClicked.handleAny {
        pageModel.update(p => Math.max(0, p - 1))
      },
      nextButton.onClicked.handleAny {
        pageModel.update(p => Math.min(pages.size - 1, p + 1))
      }
    )

    div(selected.wrap, prevButton.wrap, nextButton.wrap)
  }
}

object WizzardPage extends SimpleComponentBase with SimpleRouted {

  def title = "Wizzard"
  def path = "/wizzard"

  override def assemble(implicit c: SimpleContext): Html = {
    val page1   = SimpleText("This is page 1")
    val page2   = SimpleText("This is page 2")
    val page3   = SimpleText("This is page 3")
    val wizzard = Wizzard(
      Seq(
        page1,
        page2,
        page3
      )
    )
    div(
      h2("Wizzard"),
      div(
        "This example shows to implement a simple wizzard"
      ),
      wizzard.wrap
    )
  }
}
