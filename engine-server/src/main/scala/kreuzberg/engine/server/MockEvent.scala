package kreuzberg.engine.server

import io.circe.JsonObject
import org.scalajs.dom.{Event, KeyboardEvent, MouseEvent}

/**
 * Server-side stand-ins for DOM events, built from the fields the client sends. Keyboard and mouse events can be
 * matched as [[KeyboardEvent]] / [[MouseEvent]]. `preventDefault` is decided by the client, using the listener flags.
 */
private[server] object MockEvent {

  def apply(data: JsonObject): Event = {
    val fields = Fields(data)
    if (data.contains("key")) new MockKeyboardEvent(fields)
    else if (data.contains("button")) new MockMouseEvent(fields)
    else new MockEvent(fields)
  }

  private class MockEvent(fields: Fields) extends Event {
    override def `type`: String         = fields.string("type")
    override def preventDefault(): Unit = ()
  }

  private class MockKeyboardEvent(fields: Fields) extends MockEvent(fields) with KeyboardEvent with Modifiers(fields) {
    override def key: String  = fields.string("key")
    override def code: String = fields.string("code")
  }

  private class MockMouseEvent(fields: Fields) extends MockEvent(fields) with MouseEvent with Modifiers(fields) {
    override def button: Int     = fields.number("button").toInt
    override def clientX: Double = fields.number("clientX")
    override def clientY: Double = fields.number("clientY")
  }

  private trait Modifiers(fields: Fields) {
    def altKey: Boolean   = fields.boolean("altKey")
    def ctrlKey: Boolean  = fields.boolean("ctrlKey")
    def shiftKey: Boolean = fields.boolean("shiftKey")
    def metaKey: Boolean  = fields.boolean("metaKey")
  }

  private case class Fields(data: JsonObject) {
    def string(name: String): String   = data(name).flatMap(_.asString).getOrElse("")
    def number(name: String): Double   = data(name).flatMap(_.asNumber).map(_.toDouble).getOrElse(0.0)
    def boolean(name: String): Boolean = data(name).flatMap(_.asBoolean).getOrElse(false)
  }
}
