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

object TodoAdderForm extends SimpleComponentBase {
  def assemble(implicit c: SimpleContext): Html = {
    val textInput = TextInput("name")
    val button    = Button("Add")
    add(
      from(button.clicked)
        .withState(textInput)(_.text)
        .triggerChannel(addEvent)
    )
    form(
      label("Element: "),
      textInput.wrap,
      button.wrap
    )
  }

  val addEvent = Channel.create[String]()
  // val addEvent = Event.Custom[String]("add")
}

object TodoPageWithApi extends SimpleComponentBase {

  private def decodeResult(t: Try[Seq[String]]): LoadingModel[TodoList] = {
    t.fold(t => LoadingModel.Error(Failure.fromThrowable(t)), x => LoadingModel.Loaded(TodoList(x)))
  }

  val model = Model.create[LoadingModel[TodoList]](LoadingModel.Loading)

  def assemble(implicit c: SimpleContext): Html = {
    val todolist                             = subscribe(model)
    given provider: Provider[Lister[Future]] = stubProvider[Lister[Future]]
    val lister                               = provide[Lister[Future]]

    if (todolist == LoadingModel.Loading) {
      add {
        EventSource.Assembled
          .effect(_ => lister.listItems())
          .intoModel(model)(decodeResult)
      }
    }

    todolist match {
      case LoadingModel.Error(e)  => div(s"Could not load model ${e}")
      case LoadingModel.Loading   => div("Loading")
      case LoadingModel.Loaded(v) =>
        val shower = TodoShower(v)
        val form   = TodoAdderForm

        add(
          from(form.addEvent)
            .effect(text => lister.addItem(text))
            .intoModel(model)(_ => LoadingModel.Loading)
            .and
            .effect(_ => lister.listItems())
            .intoModel(model)(decodeResult)
        )

        div(
          shower.wrap,
          form.wrap
        )
    }
  }
}
