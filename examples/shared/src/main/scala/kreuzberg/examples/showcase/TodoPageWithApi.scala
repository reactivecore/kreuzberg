package kreuzberg.examples.showcase
import kreuzberg.*
import kreuzberg.imperative.*
import kreuzberg.scalatags.all.*
import kreuzberg.scalatags.*
import kreuzberg.rpc.*
import scala.concurrent.Future
import kreuzberg.rpc.StubProvider.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

sealed trait LoadingModel[+T]

object LoadingModel {
  object Loading                  extends LoadingModel[Nothing]
  case class Loaded[+T](value: T) extends LoadingModel[T]
  case class Error(f: Throwable)  extends LoadingModel[Nothing] // TODO: Use RPC Errors
}

object TodoPageWithApi extends SimpleComponentBase {

  private def decodeResult(t: Try[Seq[String]]): LoadingModel[TodoList] = {
    t.fold(t => LoadingModel.Error(Failure.fromThrowable(t)), x => LoadingModel.Loaded(TodoList(x)))
  }

  def assemble(implicit c: SimpleContext): Html = {
    val m                                    = model[LoadingModel[TodoList]]("api_todolist", LoadingModel.Loading)
    val todolist                             = subscribe(m)
    given provider: Provider[Lister[Future]] = stubProvider[Lister[Future]]
    val lister                               = provide[Lister[Future]]

    if (todolist == LoadingModel.Loading) {
      add {
        EventSource.FutureEvent(lister.listItems()).intoModel(m)(decodeResult)
      }
    }

    todolist match {
      case LoadingModel.Error(e)  => div(s"Could not load model ${e}")
      case LoadingModel.Loading   => div("Loading")
      case LoadingModel.Loaded(v) =>
        val shower    = child("shower", TodoShower(v))
        val textInput = child("input", TextInput("name"))
        val button    = child("addButton", Button("Add"))

        add(
          from(button)(_.clicked)
            .withState(textInput)(_.text)
            .flatMap { case (_, text) =>
              EventSource.FutureEvent(lister.addItem(text))
            }
            .flatMap { _ =>
              EventSource.FutureEvent(lister.listItems())
            }
            .intoModel(m)(decodeResult)
        )

        div(
          shower.wrap,
          textInput.wrap,
          button.wrap
        )
    }
  }
}
