package kreuzberg.examples.showcase.todo

import kreuzberg.*
import kreuzberg.examples.showcase.components.{Button, TextInput}
import kreuzberg.examples.showcase.*
import kreuzberg.examples.showcase.todo.TodoPageWithApi.provide
import kreuzberg.extras.{LazyLoader, SimpleRouter}
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
        .handleAny {
          val entry = textInput.text.read
          onAdd.trigger(entry)
        }
    )
    add(
      clear.handleAny {
        textInput.text.set("")
      }
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
  override def load()(using c: AssemblerContext): Effect[TodoList] = {
    val api = provide[Api]
    Effect.future { api.todoApi.listItems() }.map(response => TodoList.apply(response.items))
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

    addHandler(form.onAdd) { text =>
      lister.addItem(text).foreach { _ =>
        LazyTodoViewer.refresh.trigger(())
        form.clear.trigger(())
      }
    }

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
