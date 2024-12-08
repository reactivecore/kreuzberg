package kreuzberg.extras.forms

import kreuzberg.*
import kreuzberg.RuntimeState.JsProperty
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import org.scalajs.dom.html.Input

/** Responsible for displaying a form. */
case class FormComponent[T](
    form: Form[T],
    default: T,
    fieldBuilder: FormFieldComponentBuilder = FormFieldComponentBuilder.Default
) extends SimpleComponentBase {

  val splitDefault = form.codec.encode(default)
  val components   = form.fields.zip(splitDefault).map { (formElement, value) =>
    fieldBuilder.build(formElement, value)
  }

  override def assemble(using c: SimpleContext): Html = {
    div(
      components.map(_.wrap)
    )
  }

  /** State of the fields. */
  def fieldsState: RuntimeState[Seq[String]] = {
    RuntimeState.Collect(components.map(_.text))
  }

  /** Decoded state, without validation */
  def decoded: RuntimeState[Result[T]] = {
    fieldsState.map { fields =>
      form.codec.decode(fields.toList)
    }
  }

  /** Full validated state. */
  def validatedState: RuntimeState[Result[T]] = {
    decoded.map { decoded =>
      for {
        value <- decoded
        _     <- form.validator.validated(value)
      } yield {
        value
      }
    }
  }
}

trait FormFieldComponent extends Component {
  def text: RuntimeState[String]
}

object FormFieldComponent {

  /** Input inside form field. */
  case class FormFieldInput(field: FormField[?], initialValue: String) extends SimpleComponentBase {
    override def assemble(using c: SimpleContext): Html = {
      input(
        name   := field.name,
        `type` := field.formType,
        value  := initialValue,
        if (field.required) required
      )
    }

    override type DomElement = Input
    def onChange     = jsEvent("change")
    def onInputEvent = jsEvent("input")

    // Workaround to make it also usable with Checkboxes
    val text: JsProperty[DomElement, String] = if (field.formType == "checkbox") {
      jsProperty(_.checked.toString, (r, v) => r.checked = v.toBooleanOption.getOrElse(false))
    } else {
      jsProperty(_.value, (r, v) => r.value = v)
    }
  }

  case class FormFieldViolationsComponent(violations: Subscribeable[List[String]]) extends SimpleComponentBase {
    override def assemble(using c: SimpleContext): Html = {
      val got = violations.subscribe()
      if (got.isEmpty) {
        span()
      } else {
        ul(
          got.map(li(_))
        )
      }
    }
  }

  case class Default(field: FormField[?], initialValue: String) extends SimpleComponentBase with FormFieldComponent {
    val input               = FormFieldInput(field, initialValue)
    val violations          = Model.create[List[String]](Nil)
    val violationsComponent = FormFieldViolationsComponent(violations)

    override def assemble(using c: SimpleContext): Html = {
      add(
        input.onInputEvent.handle { _ =>
          val value   = input.text.read()
          val decoded = field.decodeAndValidate(value).fold(_.asList, _ => Nil)
          violations.set(decoded)
        }
      )
      div(
        label(
          `for` := field.name,
          field.label
        ),
        input,
        violationsComponent
      )
    }

    override def text: RuntimeState[String] = input.text
  }
}

trait FormFieldComponentBuilder {
  def build(field: FormField[?], initialValue: String): FormFieldComponent
}

object FormFieldComponentBuilder {
  object Default extends FormFieldComponentBuilder {
    override def build(field: FormField[?], initialValue: String): FormFieldComponent = {
      FormFieldComponent.Default(field, initialValue)
    }
  }
}
