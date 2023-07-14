package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import scala.concurrent.duration._

case object Counter extends SimpleComponentBase {
  val counterModel = Model.create[Int](1)

  override def assemble(using c: SimpleContext): Html = {
    val counter = subscribe(counterModel)

    add(
      EventSource
        .Timer(1.second, true)
        .changeModelDirect(counterModel) { x => x + 1 }
    )
    all.span(s"Showing for ${counter} seconds")
  }
}

object IndexPage extends ComponentBase {
  def assemble(using context: AssemblerContext): Assembly = {
    div(
      h2("Hi There"),
      "Welcome to this small Kreuzberg Demonstration",
      div(
        Counter
      )
    )
  }
}
