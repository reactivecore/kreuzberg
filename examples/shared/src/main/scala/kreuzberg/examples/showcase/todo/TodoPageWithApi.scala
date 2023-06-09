package kreuzberg.examples.showcase.todo

import kreuzberg.*
import kreuzberg.examples.showcase.components.{Button, TextInput}
import kreuzberg.examples.showcase.todo.TodoList
import kreuzberg.examples.showcase.*
import kreuzberg.rpc.*
import kreuzberg.rpc.StubProvider.stubProvider
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

sealed trait LoadingState[+T]

object LoadingState {
  object Loading                  extends LoadingState[Nothing]
  case class Loaded[+T](value: T) extends LoadingState[T]
  case class Error(f: Throwable)  extends LoadingState[Nothing]
}

object TodoAdderForm extends SimpleComponentBase {

  private val onSubmit = jsEvent("submit", true)

  def assemble(implicit c: SimpleContext): Html = {
    val textInput = TextInput("name")
    val button    = Button("Add")
    add(
      onSubmit
        .or(button.onClicked)
        .withState(textInput.text)
        .trigger(onAdd)
    )
    form(
      label("Element: "),
      textInput.wrap,
      button.wrap
    )
  }

  val onAdd = Channel.create[String]()
}

object TodoPageWithApi extends SimpleComponentBase {

  private def decodeResult(t: Try[Seq[String]]): LoadingState[TodoList] = {
    t.fold(t => LoadingState.Error(Failure.fromThrowable(t)), x => LoadingState.Loaded(TodoList(x)))
  }

  val model = Model.create[LoadingState[TodoList]](LoadingState.Loading)

  def assemble(implicit c: SimpleContext): Html = {
    val todolist = subscribe(model)
    val lister   = provide[TodoApi[Future]]

    if (todolist == LoadingState.Loading) {
      add {
        EventSource.Assembled
          .effect(_ => lister.listItems())
          .intoModel(model)(decodeResult)
      }
    }

    todolist match {
      case LoadingState.Error(e)  => div(s"Could not load model ${e}")
      case LoadingState.Loading   => div("Loading...")
      case LoadingState.Loaded(v) =>
        val shower = TodoShower(v)
        val form   = TodoAdderForm

        add(
          form.onAdd
            .effect(text => lister.addItem(text))
            .intoModel(model)(_ => LoadingState.Loading)
            .and
            .effect(_ => lister.listItems())
            .intoModel(model)(decodeResult)
        )

        div(
          h2("API Based TODO App"),
          div(
            "This example shows how to use auto-generated API-Interfaces to implement a TODO App"
          ),
          shower.wrap,
          form.wrap
        )
    }
  }
}
