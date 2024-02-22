package kreuzberg.examples.showcase.todo

import kreuzberg.*
import kreuzberg.examples.showcase.components.{Button, TextInput}
import kreuzberg.examples.showcase.*
import kreuzberg.examples.showcase.todo.TodoPageWithApi.provide
import kreuzberg.extras.LazyLoader
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

import scala.concurrent.Future
import scala.util.Try

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
    add(
      clear
        .map(_ => "")
        .intoProperty(textInput.text)
        .and
        .executeCode(_ => println("Cleared?!"))
    )
    form(
      label("Element: "),
      textInput.wrap,
      button.wrap
    )
  }

  val onAdd = Channel.create[String]()
  val clear = Channel.create[Any]()
}

object LazyTodoViewer extends LazyLoader[TodoList] {
  override def load()(using c: ServiceRepository): Effect[TodoList] = {
    val api = provide[Api]
    Effect.future { _ => api.todoApi.listItems() }.map(response => TodoList.apply(response.items))
  }

  override def ok(data: TodoList)(using c: SimpleContext): Html = {
    div(
      TodoShower(Model.create(data))
    )
  }
}

object TodoPageWithApi extends SimpleComponentBase {

  def assemble(implicit c: SimpleContext): Html = {
    val lister = provide[Api].todoApi
    val form   = TodoAdderForm

    add(
      form.onAdd
        .future(text => lister.addItem(text))
        .to(LazyTodoViewer.refresh)
        .and
        .trigger(form.clear)
    )

    div(
      h2("API Based TODO App"),
      div(
        "This example shows how to use auto-generated API-Interfaces to implement a TODO App"
      ),
      LazyTodoViewer.wrap,
      form.wrap
    )
  }
}
