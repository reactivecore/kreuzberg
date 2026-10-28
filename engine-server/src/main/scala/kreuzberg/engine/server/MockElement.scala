package kreuzberg.engine.server

import io.circe.Json
import kreuzberg.{Html, Identifier, SimpleHtml}
import kreuzberg.scalatags.ScalaTagsHtml
import kreuzberg.xml.ScalaXmlHtml
import org.scalajs.dom.{Element, html}

/**
 * Server-side stand-ins for DOM elements.
 *
 * Reading returns the value the browser sent with the current event (the client sends `value` and `checked` of all form
 * inputs). Writing updates that state and sets the property in the browser.
 */
private[server] object MockElement {

  /** Create a mock element, picking the variant by its HTML tag. */
  def apply(session: Session, id: Identifier, tag: Option[String]): Element = {
    val state = InputState(session, id)
    tag match {
      case Some("select")   => new MockSelect(state)
      case Some("textarea") => new MockTextArea(state)
      case _                => new MockInput(state)
    }
  }

  /** Root HTML tag name (lower case), if known. */
  def rootTag(html: Html): Option[String] = {
    val tag = html match {
      case s: SimpleHtml    => Some(s.tag)
      case s: ScalaTagsHtml => Some(s.tag.tag)
      case s: ScalaXmlHtml  => Some(s.elem.label)
      case _                => None
    }
    tag.map(_.toLowerCase)
  }

  private class MockInput(state: InputState) extends html.Input {
    def value: String               = state.string("value")
    def value_=(v: String): Unit    = state.set("value", Json.fromString(v))
    def checked: Boolean            = state.boolean("checked")
    def checked_=(v: Boolean): Unit = state.set("checked", Json.fromBoolean(v))
  }

  private class MockSelect(state: InputState) extends html.Select {
    def value: String            = state.string("value")
    def value_=(v: String): Unit = state.set("value", Json.fromString(v))
  }

  private class MockTextArea(state: InputState) extends html.TextArea {
    def value: String            = state.string("value")
    def value_=(v: String): Unit = state.set("value", Json.fromString(v))
  }

  private case class InputState(session: Session, id: Identifier) {
    private def get(property: String): Option[Json] = session.inputs.get(id).flatMap(_(property))

    def string(property: String): String = get(property).flatMap(_.asString).getOrElse("")

    def boolean(property: String): Boolean = get(property).flatMap(_.asBoolean).getOrElse(false)

    def set(property: String, value: Json): Unit = {
      val current = session.inputs.getOrElse(id, io.circe.JsonObject.empty)
      session.inputs += (id -> current.add(property, value))
      session.pendingWrites :+= Session.PropertyWrite(id, property, value)
    }
  }
}
