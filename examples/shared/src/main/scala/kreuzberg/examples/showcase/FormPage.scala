package kreuzberg.examples.showcase

import kreuzberg._
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

import scala.util.{Success, Try}

object FormPage extends ComponentBase {
  override def assemble: AssemblyResult = {
    for {
      nameForm  <- namedChild(
                     "name",
                     ValidatingTextInput(
                       "name",
                       s => {
                         if (s.length < 3) {
                           Some("Must contain at least 3 characters")
                         } else {
                           None
                         }
                       }
                     )
                   )
      ageForm   <- namedChild(
                     "age",
                     ValidatingTextInput(
                       "age",
                       s => {
                         if (Try(s.toInt).isSuccess) {
                           None
                         } else {
                           Some("Must be a number")
                         }
                       }
                     )
                   )
    } yield {
      Assembly(
        form(),
        Vector(nameForm, ageForm)
      )
    }
  }
}
