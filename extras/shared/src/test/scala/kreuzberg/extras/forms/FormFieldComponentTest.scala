package kreuzberg.extras.forms

import kreuzberg.{Assembler, IdentifierFactory}
import kreuzberg.testcore.TestBase

class FormFieldComponentTest extends TestBase {

  private def render(component: => kreuzberg.Component): String = {
    Assembler.singleTree(() => IdentifierFactory.withFresh(component)).render()
  }

  it should "render required inputs" in {
    val html = render(FormFieldComponent.FormFieldInput(FormField[String]("name", required = true), "x"))
    html should include("required")
  }

  it should "render required selects with the initial option selected" in {
    val field = FormField[String]("color", required = true, options = Seq("r" -> "Red", "g" -> "Green"))
    val html  = render(FormFieldComponent.FormFieldSelect(field, "g"))
    html should include("required")
    html should include("""<option value="g" selected""")
    html should not include ("""<option value="r" selected""")
  }
}
