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

  val splitDefault: List[String]           = form.codec.encode(default)
  val components: List[FormFieldComponent] = form.fields.zip(splitDefault).map { (formElement, value) =>
    fieldBuilder.build(formElement, value)
  }

  def assemble(using sc: SimpleContext): Html = {
    div(
      components.map(_.wrap)
    )
  }

  /** State of the fields. */
  def fieldsState: RuntimeState[Seq[String]] = fieldsProperty

  /** Property State of the fields. */
  val fieldsProperty: RuntimeProperty[Seq[String]] = new RuntimeProperty[Seq[String]] {
    override def set(value: Seq[String]): Unit = {
      components.zip(value).foreach { case (c, v) => c.text.set(v) }
    }

    override def read(): Seq[String] = {
      components.map(_.text.read())
    }
  }

  /** Decoded state, without validation */
  def decoded: RuntimeState[Result[T]] = {
    fieldsState.map { fields =>
      form.codec.decode(fields.toList)
    }
  }

  /** Set a value. */
  def setValue(value: T): Unit = {
    val split = form.codec.encode(value)
    fieldsProperty.set(split)
  }

  /** Clear shown Violations */
  def clearViolations(): Unit = {
    components.foreach(_.violations.set(Nil))
  }

  /** Reset the Form State to the default. */
  def reset(): Unit = {
    setValue(default)
    clearViolations()
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

  /** The text of this field component. */
  def text: RuntimeProperty[String]

  /** The current displayed violations, should update automatically on changing content */
  def violations: Model[List[String]]
}

object FormFieldComponent {

  /** Input inside form field. */
  case class FormFieldInput(field: FormField[?], initialValue: String) extends SimpleComponentBase {
    def assemble(using sc: SimpleContext): Html = {
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
    def assemble(using sc: SimpleContext): Html = {
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

    def assemble(using sc: SimpleContext): Html = {
      add(
        input.onInputEvent.handle { _ =>
          val value   = input.text.read()
          val decoded = field.decodeAndValidate(value).fold(_.asList, _ => Nil)
          violations.set(decoded)
        }
      )
      div(
        Option.when(field.tooltip.nonEmpty) {
          title := field.tooltip
        }
      )(
        label(
          `for` := field.name,
          field.label
        ),
        input,
        Option.when(field.description.nonEmpty) {
          div(style := "color: #A9A9A9; font-size: 0.875em;")(
            field.description
          )
        },
        violationsComponent
      )
    }

    override def text: RuntimeProperty[String] = input.text
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
