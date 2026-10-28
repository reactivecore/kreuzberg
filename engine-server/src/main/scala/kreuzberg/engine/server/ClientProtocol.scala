package kreuzberg.engine.server

import io.circe.syntax.*
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}
import io.circe.{Decoder, Encoder, Json, JsonObject}
import kreuzberg.Identifier

/**
 * Protocol between the server and `kreuzberg-client.js`.
 *
 * The server sends numbered operations as JSON objects, one per line, via the stream of a page (see [[Page]]). The
 * client posts events as [[ClientProtocol.EventRequest]]. Listeners are referenced by string keys, which the server
 * generates with the bindings.
 */
private[server] object ClientProtocol {

  /** An operation for the client. Encoded as JSON object, with the lower case name in `op`. */
  enum Op {

    /** Replace the element of a component (re-render). */
    case Replace(id: Identifier, html: String)

    case Append(id: Identifier, html: String)

    case Prepend(id: Identifier, html: String)

    /** The listeners to forward; replaces the previous ones. */
    case Bindings(listeners: Seq[Listener])

    /** Set a DOM property of a component's element (e.g. `value`). */
    case Set(id: Identifier, property: String, value: Json)

    case History(title: String, url: String, push: Boolean)

    case Title(title: String)

    /** Store the locale in a cookie and reload. */
    case Locale(cookie: String, locale: String)

    case Reload

    /** Keeps the stream alive. */
    case Ping

    /** A newer page of the session was loaded (e.g. in another tab), this one stops listening. */
    case Stale
  }

  object Op {

    /** The listeners of bindings. */
    def bindings(bindings: kreuzberg.engine.server.Bindings): Op = Bindings(
      bindings.listeners.toSeq.map { case (key, listener) =>
        val target = key match {
          case kreuzberg.engine.server.Bindings.ListenerKey.Element(target, _) => Some(target)
          case kreuzberg.engine.server.Bindings.ListenerKey.Window(_, _)       => None
        }
        Listener(listenerKey(key), key.eventName, listener.preventDefault, listener.capture, target, target.isEmpty)
      }
    )

    given Encoder.AsObject[Op] = {
      given Configuration = Configuration.default.withDiscriminator("op").withTransformConstructorNames(_.toLowerCase)
      // The operation first, for reading the stream
      ConfiguredEncoder.derived[Op].mapJsonObject { o => o("op").fold(o)(op => ("op", op) +: o.remove("op")) }
    }
  }

  /**
   * A listener of the client.
   *
   * @param id
   *   component of the element to listen on
   * @param window
   *   listen on `window` instead
   */
  case class Listener(
      key: String,
      event: String,
      prevent: Boolean,
      capture: Boolean,
      id: Option[Identifier],
      window: Boolean
  ) derives Encoder.AsObject

  /** Identifiers are sent as numbers. */
  given Encoder[Identifier] = Encoder.encodeInt.contramap(_.value)

  /** Encode an operation as one line, with its number (0 for unnumbered ones like pings). */
  def line(op: Op, seq: Long = 0): String = op.asJsonObject.add("seq", seq.asJson).asJson.noSpaces + "\n"

  /**
   * Operations embedded into the page, safe inside a script element: `<` only occurs within JSON strings, where it can
   * be escaped.
   */
  def initScript(ops: Seq[Op]): String = ops.asJson.noSpaces.replace("<", "\\u003c")

  // Keys: "e:<target>:<event>" and "w:<owner>:<event>"

  def listenerKey(key: Bindings.ListenerKey): String = key match {
    case Bindings.ListenerKey.Element(target, eventName) => s"e:${target.value}:${eventName}"
    case Bindings.ListenerKey.Window(owner, eventName)   => s"w:${owner.value}:${eventName}"
  }

  def parseListenerKey(s: String): Option[Bindings.ListenerKey] = s.split(":", 3) match {
    case Array("e", id, eventName) => parseId(id).map(Bindings.ListenerKey.Element(_, eventName))
    case Array("w", id, eventName) => parseId(id).map(Bindings.ListenerKey.Window(_, eventName))
    case _                         => None
  }

  private def parseId(s: String): Option[Identifier] = s.toIntOption.map(Identifier(_))

  /**
   * An event posted by the client.
   *
   * @param listeners
   *   keys of the listeners to fire, innermost first
   * @param event
   *   serializable fields of the DOM event (`type`, `key`, `button`, ...)
   * @param inputs
   *   current `value`/`checked` of all form inputs, by component id
   * @param location
   *   path and query of the browser
   * @param received
   *   number of the last operation the client received
   */
  case class EventRequest(
      listeners: Seq[String] = Nil,
      event: JsonObject = JsonObject.empty,
      inputs: Map[String, JsonObject] = Map.empty,
      location: Option[String] = None,
      received: Long = 0
  ) {

    /** Inputs by component id. */
    def inputsById: Map[Identifier, JsonObject] = inputs.flatMap { case (k, v) => parseId(k).map(_ -> v) }
  }

  object EventRequest {
    given Decoder[EventRequest] = {
      // Missing fields take the defaults
      given Configuration = Configuration.default.withDefaults
      ConfiguredDecoder.derived[EventRequest]
    }

    /** Parse a request body; invalid bodies are rejected by the caller. */
    def parse(body: String): Option[EventRequest] = io.circe.parser.decode[EventRequest](body).toOption
  }
}
