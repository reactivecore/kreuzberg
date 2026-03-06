package kreuzberg.extras.forms

import kreuzberg.extras
import kreuzberg.extras.Error.DecodingError
import kreuzberg.extras.{Codec, Validator, Error}
import kreuzberg.testcore.TestBase

object nameValidator extends Validator[SampleForm] {
  override def validate(value: SampleForm): Option[extras.Error.ValidationError] = {
    if (value.name.isEmpty) {
      Some(extras.Error.SingleValidationError("Name should not be empty"))
    } else {
      None
    }
  }
}

@UseValidator(nameValidator)
case class SampleForm(
    @UseField(name = "niceName", required = true) name: String,
    @UseField(
      label = "Age",
      placeholder = "Please enter your age",
      validator = Validator.fromPredicate[Int](_ >= 18, "You must be at least 18")
    )
    age: Int,
    @UseField(
      label = "Color",
      options = Seq("red" -> "Red", "green" -> "Green", "blue" -> "Blue")
    )
    color: String = "red"
)

class GeneratorTest extends TestBase {

  it should "generate an encoder" in {
    val encoder = Generator.buildEncoder[SampleForm]
    encoder(SampleForm("Hello", 42, "red")) shouldBe List("Hello", "42", "red")
  }

  it should "generate an decoder" in {
    val decoder = Generator.buildDecoder[SampleForm]
    decoder(Array("Hello", "42", "red")) shouldBe Right(SampleForm("Hello", 42, "red"))
    decoder(Array("Hello", "Not a number", "red")) shouldBe Left(DecodingError("Invalid Integer"))
    decoder(Array("Hello")) shouldBe Left(DecodingError("Unexpected Arity"))
  }

  it should "fetch the main validator" in {
    val annotations = Generator.fetchMainAnnotations[SampleForm]
    annotations shouldBe List(
      UseValidator(nameValidator)
    )
  }

  it should "fetch the combined field validator" in {
    val validator = Generator.buildAllFieldValidator[SampleForm]
    validator.validate(SampleForm("Hello", 18, "red")) shouldBe None
    validator.validate(SampleForm("Hello", 17, "red")) shouldBe Some(
      Error.SingleValidationError("You must be at least 18", List("age"))
    )
  }

  it should "generate a whole simple form" in {
    val form = Generator.generate[SampleForm]
    form.fields.map(_.copy(validator = Validator.succeed)) shouldBe List(
      FormField(
        name = "niceName",
        label = "name",
        codec = Codec.string,
        validator = Validator.succeed,
        required = true
      ),
      FormField(
        name = "age",
        label = "Age",
        placeholder = "Please enter your age",
        formType = "number",
        codec = Codec.simpleInt,
        validator = Validator.succeed
      ),
      FormField(
        name = "color",
        label = "Color",
        codec = Codec.string,
        validator = Validator.succeed,
        options = Seq("red" -> "Red", "green" -> "Green", "blue" -> "Blue")
      )
    )
    form.fields(1).asInstanceOf[FormField[Int]].validator.validate(17).get.asList shouldBe List(
      "You must be at least 18"
    )
    form.validator
      .validate(
        SampleForm("Bob", 17, "red")
      )
      .get
      .asList shouldBe List("You must be at least 18")
  }

  it should "propagate options from UseField annotation" in {
    val form       = Generator.generate[SampleForm]
    val colorField = form.fields(2)
    colorField.options shouldBe Seq("red" -> "Red", "green" -> "Green", "blue" -> "Blue")
    colorField.name shouldBe "color"
    colorField.label shouldBe "Color"
  }

}
