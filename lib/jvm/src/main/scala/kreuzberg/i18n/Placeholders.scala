package kreuzberg.i18n

/** Analyzes placeholders (`%1`..`%9`, `%%` is a literal `%`) of message templates. */
object Placeholders {

  /** Returns the placeholder numbers used in a template. */
  def apply(template: String): Set[Int] = {
    MessageFormat.parts(template).collect { case MessageFormat.Part.Arg(n) => n }.toSet
  }

  /** Returns the number of arguments a template expects (highest placeholder). */
  def arity(template: String): Int = {
    apply(template).maxOption.getOrElse(0)
  }
}
