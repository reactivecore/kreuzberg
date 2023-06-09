package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.examples.showcase.components.{Button, ValidatingTextInput}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

import scala.util.{Success, Try}

object FormPage extends SimpleComponentBase {
  def assemble(using context: SimpleContext): Html = {
    val nameInput = ValidatingTextInput(
      "name",
      s => {
        if (s.length < 3) {
          Some("Must contain at least 3 characters")
        } else {
          None
        }
      }
    )
    val ageInput  = ValidatingTextInput(
      "age",
      s => {
        if (Try(s.toInt).isSuccess) {
          None
        } else {
          Some("Must be a number")
        }
      }
    )

    div(
      h2("Validating Form Fields Example"),
      div(
        "This example shows how to implement validating form fields"
      ),
      form(
        label("Name", `for` := "name"),
        nameInput.wrap,
        label("Age", `for`  := "age"),
        ageInput.wrap
      )
    )

  }
}
