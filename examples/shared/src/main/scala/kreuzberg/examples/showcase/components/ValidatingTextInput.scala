package kreuzberg.examples.showcase.components

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class ErrorShower(model: Model[Option[String]]) extends SimpleComponentBase {

  override def assemble(implicit c: SimpleContext): Html = {
    val current = subscribe(model)
    current match {
      case None    => div(style := "display:hidden")
      case Some(x) => div(x)
    }
  }
}

/**
 * A Simple validating text input to demonstrate the case of updating only part of the components.
 *
 * @param validator
 *   maps current value into error message
 */
case class ValidatingTextInput(
    name: String,
    validator: String => Option[String]
) extends SimpleComponentBase {

  def assemble(using context: SimpleContext): Html = {
    val valueModel   = Model.create("")
    val errorModel   = Model.create(None: Option[String])
    val initialValue = read(valueModel)

    val textInput   = TextInput(name, initialValue)
    val errorShower = ErrorShower(errorModel)

    add(
      textInput.onInputEvent
        .withState(textInput.text)
        .changeModel(valueModel) { (v, _) =>
          Logger.debug(s"Setting value to ${v}")
          v
        }
        .and
        .changeModel(errorModel) { (v, _) =>
          {
            val error = validator(v)
            Logger.debug(s"Setting error to ${error}")
            error
          }
        }
    )

    div(
      textInput.wrap,
      errorShower.wrap
    )
  }
}
