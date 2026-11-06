package kreuzberg.i18n

import kreuzberg.{Component, FlatHtmlBuilder, Html, HtmlEmbedding, Identifier, SimpleHtml, SimpleHtmlNode}

/**
 * Translates a key, with HTML elements, components or strings as placeholder arguments.
 *
 * {{{
 * // catalog: index.source: "The source code is on %1."
 * trHtml("index.source", a(href := "https://github.com/reactivecore/kreuzberg", "GitHub"))
 * }}}
 *
 * Works with every HTML flavour (ScalaTags tags and Scala XML elements convert to `Html`). The text of the catalog and
 * string arguments are escaped. As with `tr`, the key must be a string literal, and a missing key yields the key
 * itself.
 *
 * The result is a fragment without an enclosing element. Embed it into ScalaTags directly, into Scala XML via `.wrap`
 * (`{trHtml(...).wrap}`). Used as root of a component, it is wrapped into a `<span>`.
 *
 * Each argument is inserted at most once: a placeholder repeated in the template stays empty after its first use, as a
 * component can only be embedded once.
 */
inline def trHtml(inline key: String, args: (HtmlEmbedding | String)*): Html = {
  scala.compiletime.requireConst(key)
  TrHtml.render(key, args)
}

private[i18n] object TrHtml {

  def render(key: String, args: Seq[HtmlEmbedding | String]): Html = {
    Translations.current.lookupOrMissing(key) match {
      case Left(missing)   => HtmlFragment(Vector(SimpleHtmlNode.Text(missing)))
      case Right(template) => HtmlFragment(fill(template, args))
    }
  }

  private def fill(template: String, args: Seq[HtmlEmbedding | String]): Vector[SimpleHtmlNode] = {
    MessageFormat
      .parts(template)
      .foldLeft((Vector.empty[SimpleHtmlNode], Set.empty[Int])) {
        case ((result, used), MessageFormat.Part.Text(text)) => (result :+ SimpleHtmlNode.Text(text), used)
        case ((result, used), MessageFormat.Part.Arg(n))     =>
          val arg = args.lift(n - 1).filterNot(_ => used.contains(n)).map(toNode)
          (result ++ arg, used + n)
      }
      ._1
  }

  private def toNode(arg: HtmlEmbedding | String): SimpleHtmlNode = {
    arg match {
      case s: String    => SimpleHtmlNode.Text(s)
      case c: Component => SimpleHtmlNode.EmbeddedComponent(c)
      case h: Html      => SimpleHtmlNode.maybeWrap(h)
    }
  }
}

/** HTML nodes without an enclosing element; wrapped into a `<span>` when an element is needed. */
private[i18n] case class HtmlFragment(nodes: Vector[SimpleHtmlNode]) extends Html {
  override def withId(id: Identifier): Html = asSpan.withId(id)

  override def addComment(c: String): Html = asSpan.addComment(c)

  override def embeddedComponents: Iterable[Component] = nodes.flatMap(_.embeddedComponents)

  override def flatToBuilder(flatHtmlBuilder: FlatHtmlBuilder): Unit = nodes.foreach(_.flatToBuilder(flatHtmlBuilder))

  override def appendChild(html: Html): Html = copy(nodes = nodes :+ SimpleHtmlNode.maybeWrap(html))

  override def prependChild(html: Html): Html = copy(nodes = SimpleHtmlNode.maybeWrap(html) +: nodes)

  private def asSpan: SimpleHtml = SimpleHtml("span", children = nodes)
}
