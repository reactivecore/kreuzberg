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
    age: Int
)

class GeneratorTest extends TestBase {

  it should "generate an encoder" in {
    val encoder = Generator.buildEncoder[SampleForm]
    encoder(SampleForm("Hello", 42)) shouldBe List("Hello", "42")
  }

  it should "generate an decoder" in {
    val decoder = Generator.buildDecoder[SampleForm]
    decoder(Array("Hello", "42")) shouldBe Right(SampleForm("Hello", 42))
    decoder(Array("Hello", "Not a number")) shouldBe Left(DecodingError("Invalid Integer"))
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
    validator.validate(SampleForm("Hello", 18)) shouldBe None
    validator.validate(SampleForm("Hello", 17)) shouldBe Some(
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
      )
    )
    form.fields(1).asInstanceOf[FormField[Int]].validator.validate(17).get.asList shouldBe List(
      "You must be at least 18"
    )
    form.validator
      .validate(
        SampleForm("Bob", 17)
      )
      .get
      .asList shouldBe List("You must be at least 18")
  }

}
