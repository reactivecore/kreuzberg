package kreuzberg.examples.showcase.pages

import kreuzberg.examples.showcase.components.{Button, Label}
import kreuzberg.extras.forms.{Codec, Form, FormComponent, FormField, Generator, UseField, UseValidator, Validator}
import kreuzberg.{Html, Model, SimpleComponentBase, SimpleContext}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.extras.SimpleRouted

object ExtendedFormPage extends SimpleComponentBase with SimpleRouted {

  def path  = "/form2"
  def title = "Extended Form"

  @UseValidator(Validator.fromPredicate[Register](r => r.password == r.passwordRepeat, "Passwords do not match"))
  case class Register(
      @UseField(label = "First Name", validator = Validator.minLength(2))
      firstName: String = "",
      @UseField(label = "Last Name", validator = Validator.minLength(2))
      lastName: String = "",
      @UseField(label = "Email Address", validator = Validator.email)
      emailAddress: String = "",
      @UseField(label = "Password", ftype = "password", validator = Validator.minLength(6))
      password: String = "",
      @UseField(label = "Password (Repeat)", ftype = "password")
      passwordRepeat: String = "",
      @UseField(label = "Large Number", validator = Validator.fromPredicate[Int](_ > 100, "Must be large"))
      largeNumber: Int = 0,
      @UseField(label = "Is Adult", validator = Validator.fromPredicate[Boolean](_ == true, "Must be true"))
      isAdult: Boolean = false
  )

  val registerForm: Form[Register] = Generator.generate[Register]

  // Alternative (manual) notation
  val alternativeForm: Form[Register] = (
    FormField("firstName", label = "First Name", validator = Validator.minLength(2)) ::
      FormField("lastName", label = "Last Name", validator = Validator.minLength(2)) ::
      FormField("emailAddress", label = "Email address", validator = Validator.email) ::
      FormField(
        "password",
        label = "Password",
        formType = "password",
        validator = Validator.minLength(6)
      ) ::
      FormField(
        "passwordRepeat",
        label = "Password (Repeat)",
        formType = "password"
      ) ::
      FormField(
        "largeNumber",
        label = "Enter a Large number",
        formType = "number",
        codec = Codec.simpleInt,
        validator = Validator.fromPredicate(_ > 100, "Must be be large")
      ) ::
      FormField(
        "isAdult",
        label = "Is Adult",
        formType = "checkbox",
        codec = Codec.simpleBoolean,
        validator = Validator.fromPredicate[Boolean](_ == true, "Must be true")
      )
  ).xmap[Register](
    tuple => Register(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5, tuple._6, tuple._7),
    entry =>
      (
        entry.firstName,
        entry.lastName,
        entry.emailAddress,
        entry.password,
        entry.passwordRepeat,
        entry.largeNumber,
        entry.isAdult
      )
  ).chainValidator(
    Validator.fromPredicate(r => r.password == r.passwordRepeat, "Passwords do not match")
  )

  val formComponent = FormComponent(registerForm, Register())
  val okButton      = Button("Ok")
  val state         = Model.create[String]("")
  val label         = Label(state)

  override def assemble(using c: SimpleContext): Html = {

    add(
      okButton.onClicked.handleAny {
        val s = formComponent.validatedState.read().toString
        state.set(s)
      }
    )
    div(
      h2("Generated Form Example"),
      form(
        formComponent,
        okButton,
        "Current State: ",
        label
      )
    )
  }
}
