package kreuzberg.markdown

import com.vladsch.flexmark.ext.attributes.AttributesExtension
import com.vladsch.flexmark.ext.definition.DefinitionExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.{DataHolder, MutableDataSet}
import com.vladsch.flexmark.util.misc.Extension

import scala.jdk.CollectionConverters.*

/**
 * Options of [[MarkdownRenderer]].
 *
 * @param extensions
 *   flexmark extensions; by default tables, definition lists and attributes (`{.class}`)
 * @param headingIds
 *   render `id` attributes for headings, e.g. for anchors and a table of contents
 * @param headingOffset
 *   shifts heading levels, e.g. 1 renders `#` as `<h2>` (if the page already has an `<h1>`)
 * @param configure
 *   further flexmark options
 */
case class MarkdownOptions(
    extensions: Seq[Extension] = MarkdownOptions.defaultExtensions,
    headingIds: Boolean = true,
    headingOffset: Int = 0,
    configure: MutableDataSet => Unit = _ => ()
) {

  /** Flexmark options. */
  def toDataHolder: DataHolder = {
    val dataSet = new MutableDataSet()
      .set(Parser.EXTENSIONS, extensions.asJava)
      .set(HtmlRenderer.RENDER_HEADER_ID, java.lang.Boolean.valueOf(headingIds))
    configure(dataSet)
    dataSet.toImmutable
  }
}

object MarkdownOptions {

  /** Tables, definition lists and attributes. */
  def defaultExtensions: Seq[Extension] = Seq(
    TablesExtension.create(),
    DefinitionExtension.create(),
    AttributesExtension.create()
  )
}
