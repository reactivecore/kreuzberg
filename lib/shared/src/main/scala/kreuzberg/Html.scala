package kreuzberg

import scala.language.implicitConversions

/** Abstract HTML Code which can contain other HTML Snippets and embedded Components. */
trait Html {

  /** Appends the data.id attribut to the HTML Code. */
  def withId(id: Identifier): Html

  /** Add some comment to the HTML Comment. */
  def addComment(c: String): Html

  /** Returns all embedded components within the HTML Code. */
  def embeddedComponents: Iterable[Component]

  /** Render the HTML. */
  def render(sb: StringBuilder): Unit = {
    val nodeRender: (Identifier, StringBuilder) => Unit = { (id, builder) =>
      builder ++= s"<component id=\"${id}\"/>"
    }

    flat().render(sb, nodeRender)
  }

  /** Render the HTML to a String. */
  def renderToString(): String = {
    val sb = new StringBuilder()
    render(sb)
    sb.toString()
  }

  override def toString: String = {
    renderToString()
  }

  /** Convert to a flat HTML representation. */
  def flat(): FlatHtml = {
    val builder = FlatHtmlBuilder()
    flatToBuilder(builder)
    builder.result()
  }

  /** Serializes into a FlatHtmlBuilder. */
  def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit

  /** Append a child HTML Node. */
  def appendChild(html: Html): Html

  /** Prepend a child Node. */
  def prependChild(html: Html): Html
}

object Html {

  /** Empty HTML (used as placeholder for Services) */
  private[kreuzberg] object Empty extends Html {
    override def withId(id: Identifier): Html = this

    override def addComment(c: String): Html = this

    override def embeddedComponents: Iterable[Component] = Nil

    override def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit = {}

    override def appendChild(html: Html): Html = this

    override def prependChild(html: Html): Html = this
  }
}
