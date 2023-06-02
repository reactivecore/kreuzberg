package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

import scala.util.{Success, Try}

object FormPage extends ComponentBase {
  def assemble(using context: AssemblerContext): Assembly = {
    val nameForm = ValidatingTextInput(
      "name",
      s => {
        if (s.length < 3) {
          Some("Must contain at least 3 characters")
        } else {
          None
        }
      }
    )
    val ageForm  = ValidatingTextInput(
      "age",
      s => {
        if (Try(s.toInt).isSuccess) {
          None
        } else {
          Some("Must be a number")
        }
      }
    )

    Assembly(
      form(nameForm.wrap, ageForm.wrap)
    )
  }
}
