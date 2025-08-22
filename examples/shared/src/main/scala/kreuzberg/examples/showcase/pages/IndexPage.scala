package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.examples.showcase.components.Button
import kreuzberg.extras.{LocalStorage, Route, SimpleRouted}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

import scala.concurrent.duration.*

case object Counter extends SimpleComponentBase {
  def model = IndexPage.secondCounter.model

  def assemble(using sc: SimpleContext): Html = {
    val counter = subscribe(model)

    addHandlerAny(EventSource.Timer(1.second, true)) {
      model.update(_ + 1)
    }

    all.span(s"Showing for ${counter} seconds")
  }
}

object IndexPage extends SimpleComponentBase with SimpleRouted {
  val secondCounter = LocalStorage[Int](1, "local_storage_counter", _.toString, _.toInt)
  val count         = Model.create(0)

  val countIncrementer = Button(count.map { i => s"Clicked ${i} times" })

  def assemble(using sc: SimpleContext): Html = {
    add(
      countIncrementer.onClicked.handle { _ =>
        count.update(_ + 1)
      }
    )
    addService(secondCounter)
    div(
      h2("Hi There"),
      "Welcome to this small Kreuzberg Demonstration",
      div(
        Counter,
        br,
        countIncrementer
      )
    )
  }

  override def path  = "/"
  override def title = "Welcome"
}
