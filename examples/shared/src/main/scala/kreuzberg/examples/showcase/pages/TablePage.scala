package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.examples.showcase.components.Button
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.extras.{SimpleRouted, UrlPath}
import kreuzberg.extras.forms.{Form, FormComponent}
import kreuzberg.extras.tables.{GenericTableComponent, Tabular, UseTableColumn}

case class User(
    @UseTableColumn[Int]("User Id")
    userId: Int,
    name: String,
    active: Boolean
) derives Tabular,
      Form

object TablePage extends SimpleComponentBase with SimpleRouted {
  val initialValues = Seq(
    User(1, "Alice", true),
    User(2, "Bob", false)
  )

  def assemble(using sc: SimpleContext): Html = {
    val data          = Model.create[Seq[User]](initialValues)
    val tableView     = GenericTableComponent(data)
    val formComponent = FormComponent(User.derived$Form, User(0, "", false))
    val addButton     = Button("Add")
    addHandlerAny(addButton.onClicked) {
      formComponent.validatedState.read().toOption.foreach { user =>
        data.update(_ :+ user)
      }
    }
    div(
      tableView,
      formComponent,
      addButton
    )
  }

  override def path: UrlPath = "/table"

  override def title: String = "Table Example"
}
