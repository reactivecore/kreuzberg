package kreuzberg.i18n

/** Analyzes placeholders (`%1`..`%9`, `%%` is a literal `%`) of message templates. */
object Placeholders {

  /** Returns the placeholder numbers used in a template. */
  def apply(template: String): Set[Int] = {
    PlaceholderRegex.findAllMatchIn(template).collect { case m if m.group(1) != "%" => m.group(1).toInt }.toSet
  }

  /** Returns the number of arguments a template expects (highest placeholder). */
  def arity(template: String): Int = {
    apply(template).maxOption.getOrElse(0)
  }

  private val PlaceholderRegex = "%([1-9%])".r
}
