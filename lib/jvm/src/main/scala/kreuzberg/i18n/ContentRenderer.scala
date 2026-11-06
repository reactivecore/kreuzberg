package kreuzberg.i18n

/** Renders content files (e.g. Markdown) to HTML on the server, see [[LocalizedContent]]. */
trait ContentRenderer {

  /** File extension of the sources, without dot (e.g. `md`). */
  def extension: String

  /** Renders a source to HTML. The source is trusted, it comes from the application's resources. */
  def render(source: String): String
}

object ContentRenderer {

  /** HTML files, delivered as they are. */
  object Html extends ContentRenderer {
    override def extension: String              = "html"
    override def render(source: String): String = source
  }
}
