package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.examples.showcase.components.{Label, TextInput, XmlLabel}
import kreuzberg.xml.*
import kreuzberg.extras.SimpleRouted

object XmlPage extends SimpleComponentBase with SimpleRouted {
  def title = "XML"
  def path  = "/xml"

  override def assemble(implicit c: SimpleContext): Html = {
    val value          = Model.create("")
    val editor         = TextInput("field")
    val scalaTagsLabel = Label(value)
    val xmlLabel       = XmlLabel(value)

    addHandlerAny(editor.onInputEvent) {
      val v = editor.text.read()
      value.set(v)
    }

    <div>
      <h2>XML Example</h2>
      <div>
        This examples show the use of Scala XML.
      </div>

      <label for="field">Please enter something</label>
      {editor.wrap}
      <div>
        You entered: {scalaTagsLabel.wrap} (rendered using ScalaTags)
      </div>
      <div>
        You entered: {xmlLabel.wrap} (rendered using XML)
      </div>
    </div>
  }
}
