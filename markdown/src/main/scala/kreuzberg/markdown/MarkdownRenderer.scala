package kreuzberg.markdown

import com.vladsch.flexmark.ast.Heading
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.renderer.HeaderIdGenerator
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.{Document, TextCollectingVisitor}
import com.vladsch.flexmark.util.data.DataHolder
import kreuzberg.i18n.ContentRenderer

import scala.jdk.CollectionConverters.*

/**
 * Renders Markdown with flexmark, e.g. for `ContentConfig(documents, MarkdownRenderer())`.
 *
 * Customizable via [[MarkdownOptions]] or by overriding the steps [[parse]], [[transform]] and [[renderDocument]]. For
 * example, a table of contents can be added in `renderDocument`, using [[headings]].
 */
class MarkdownRenderer(options: MarkdownOptions = MarkdownOptions()) extends ContentRenderer {

  // Not overridable: parser and renderer are built from it during construction; use MarkdownOptions.configure instead
  private val flexmarkOptions: DataHolder = options.toDataHolder

  private val parser   = Parser.builder(flexmarkOptions).build()
  private val renderer = HtmlRenderer.builder(flexmarkOptions).build()

  override def extension: String = "md"

  override final def render(source: String): String = {
    val document = parse(source)
    transform(document)
    renderDocument(document)
  }

  /** Parses the source. */
  def parse(source: String): Document = parser.parse(source)

  /** Changes the document before rendering; by default shifts heading levels by `headingOffset`. */
  def transform(document: Document): Unit = {
    if (options.headingOffset != 0) {
      headingNodes(document).foreach { h =>
        h.setLevel((h.getLevel + options.headingOffset).max(1).min(6))
      }
    }
  }

  /** Renders the document to HTML. */
  def renderDocument(document: Document): String = renderer.render(document)

  /** Headings of a document, with the ids the renderer uses (if `headingIds` is enabled). */
  def headings(document: Document): Seq[MarkdownHeading] = {
    val ids  = new HeaderIdGenerator()
    ids.generateIds(document)
    val text = new TextCollectingVisitor()
    headingNodes(document).map { h =>
      MarkdownHeading(h.getLevel, text.collectAndGetText(h).trim, ids.getId(h))
    }
  }

  private def headingNodes(document: Document): Seq[Heading] = {
    document.getDescendants.asScala.collect { case h: Heading => h }.toSeq
  }
}

/** A heading of a Markdown document. */
case class MarkdownHeading(level: Int, text: String, id: String)
