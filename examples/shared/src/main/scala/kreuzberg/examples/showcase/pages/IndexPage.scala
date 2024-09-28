package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.examples.showcase.components.Button
import kreuzberg.extras.LocalStorage
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

import scala.concurrent.duration.*

case object Counter extends SimpleComponentBase {
  def model = IndexPage.secondCounter.model

  override def assemble(using c: SimpleContext): Html = {
    val counter = subscribe(model)

    addHandler(EventSource.Timer(1.second, true)) { _ =>
      model.set(model.read + 1)
    }

    all.span(s"Showing for ${counter} seconds")
  }
}

object IndexPage extends SimpleComponentBase {
  val secondCounter = LocalStorage[Int](1, "local_storage_counter", _.toString, _.toInt)
  val count         = Model.create(0)

  val countIncrementer = Button(count.map { i => s"Clicked ${i} times" })

  def assemble(using context: SimpleContext): Html = {
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
}
