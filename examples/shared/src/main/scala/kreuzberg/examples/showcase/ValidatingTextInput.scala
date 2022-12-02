package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.imperative.{SimpleContext, SimpleComponentBase}
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

/** A Simple validating text input to demonstrate the case of updating only part of the components.
  *
  * @param validator
  *   maps current value into error message
  */
case class ValidatingTextInput(
    name: String,
    validator: String => Option[String]
) extends ComponentBase {
  override def assemble: AssemblyResult = {
    for {
      valueModel   <- Model.make("value", "")
      initialValue <- read(valueModel)
      textInput    <- namedChild("input", TextInput(name, initialValue))
      errorModel   <- Model.make("error", None: Option[String])
      errorShower  <- namedChild("error", ErrorShower(errorModel))
      bindError     = EventBinding(
                        from(textInput)(_.inputEvent)
                          .withReplacedState(textInput)(_.text),
                        EventSink
                          .ModelChange[String, String](
                            valueModel,
                            { (v, _) =>
                              Logger.debug(s"Setting value to ${v}")
                              v
                            }
                          )
                          .and(
                            EventSink.ModelChange(
                              errorModel,
                              (v, _) => {
                                val error = validator(v)
                                Logger.debug(s"Setting error to ${error}")
                                error
                              }
                            )
                          )
                      )
    } yield {
      Assembly(
        div(),
        Vector(textInput, errorShower),
        Vector(bindError)
      )
    }
  }
}
