package kreuzberg.engine.server

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import kreuzberg.Identifier
import kreuzberg.testcore.TestBase
import org.scalajs.dom.{KeyboardEvent, MouseEvent}

import java.nio.charset.StandardCharsets
import scala.compiletime.constValueTuple
import scala.deriving.Mirror

class ClientProtocolTest extends TestBase {
  import ClientProtocol.*

  "line" should "encode one numbered operation per line, even with line breaks in HTML" in {
    line(Op.Replace(Identifier(5), "<div>\n  a\r\n</div>"), seq = 7) shouldBe
      """{"op":"replace","id":5,"html":"<div>\n  a\r\n</div>","seq":7}""" + "\n"
    line(
      Op.History("T", "/x?a=1", push = true)
    ) shouldBe """{"op":"history","title":"T","url":"/x?a=1","push":true,"seq":0}""" + "\n"
    line(Op.Set(Identifier(3), "value", Json.fromString(""))) shouldBe
      """{"op":"set","id":3,"property":"value","value":"","seq":0}""" + "\n"
  }

  "encoding" should "flatten operations with their lower case name" in {
    def json(op: Op): String = op.asJson.noSpaces
    json(Op.Append(Identifier(1), "<b>")) shouldBe """{"op":"append","id":1,"html":"<b>"}"""
    json(Op.Title("T")) shouldBe """{"op":"title","title":"T"}"""
    json(Op.Locale("lang", "de")) shouldBe """{"op":"locale","cookie":"lang","locale":"de"}"""
    json(Op.Reload) shouldBe """{"op":"reload"}"""
    json(Op.Stale) shouldBe """{"op":"stale"}"""
    json(Op.Bindings(Seq(Listener("e:1:click", "click", true, false, Some(Identifier(1)), false)))) shouldBe
      """{"op":"bindings","listeners":[{"key":"e:1:click","event":"click","prevent":true,"capture":false,"id":1,"window":false}]}"""
  }

  "the client" should "handle every operation, and only those" in {
    val client     = clientScript
    val operations = opNames
    operations.foreach { name =>
      withClue(name) {
        client should include(s""""${name}"""")
      }
    }
    val handled    = """case "(\w+)":""".r.findAllMatchIn(client).map(_.group(1)).toSet
    handled -- operations shouldBe empty
  }

  it should "send every field of an event request" in {
    val client = clientScript
    labels[EventRequest].foreach { field =>
      withClue(field) {
        client should (include(s"request.${field} =") or include(s"{ ${field}:") or include(s", ${field}:"))
      }
    }
  }

  /** Names of all operations, as encoded. */
  private def opNames: Set[String] = labels[Op].map(_.toLowerCase).toSet

  /** Names of the cases or fields of a type. */
  private inline def labels[T](using m: Mirror.Of[T]): List[String] = {
    constValueTuple[m.MirroredElemLabels].toList.map(_.toString)
  }

  private def clientScript: String = {
    val stream = getClass.getClassLoader.getResourceAsStream("kreuzberg/engine/server/client/kreuzberg-client.js")
    try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
    finally stream.close()
  }

  "keys" should "round trip" in {
    val element = Bindings.ListenerKey.Element(Identifier(5), "click")
    val window  = Bindings.ListenerKey.Window(Identifier(2), "popstate")
    parseListenerKey(listenerKey(element)) shouldBe Some(element)
    parseListenerKey(listenerKey(window)) shouldBe Some(window)
  }

  it should "reject invalid keys" in {
    parseListenerKey("x:5:click") shouldBe None
    parseListenerKey("e:abc:click") shouldBe None
  }

  "EventRequest" should "be parsed" in {
    val request = EventRequest.parse(TestServer.eventBody(listeners = Seq("e:5:click"), inputs = Map(3 -> "hi")))
    request.map(_.listeners) shouldBe Some(Seq("e:5:click"))
    request.map(_.inputsById(Identifier(3))("value")) shouldBe Some(Some(Json.fromString("hi")))
    EventRequest.parse("{}") shouldBe Some(EventRequest())
    EventRequest.parse("no json") shouldBe None
  }

  "MockEvent" should "provide keyboard and mouse data" in {
    MockEvent(JsonObject("type" -> Json.fromString("keydown"), "key" -> Json.fromString("Enter"))) match {
      case k: KeyboardEvent => (k.`type`, k.key) shouldBe ("keydown", "Enter")
      case other            => fail(s"No keyboard event: ${other}")
    }
    MockEvent(
      JsonObject("type" -> Json.fromString("click"), "button" -> Json.fromInt(2), "ctrlKey" -> Json.True)
    ) match {
      case m: MouseEvent => (m.button, m.ctrlKey) shouldBe (2, true)
      case other         => fail(s"No mouse event: ${other}")
    }
  }
}
