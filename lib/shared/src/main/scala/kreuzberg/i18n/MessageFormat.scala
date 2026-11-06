package kreuzberg.i18n

/** Formats messages with positional placeholders `%1`..`%9`; `%%` is a literal `%`. */
object MessageFormat {

  /** Substitutes placeholders. A placeholder without argument is substituted with nothing. */
  def format(template: String, args: Seq[Any]): String = {
    if (template.indexOf('%') < 0) {
      template
    } else {
      parts(template).map {
        case Part.Text(text) => text
        case Part.Arg(n)     => args.lift(n - 1).fold("")(String.valueOf)
      }.mkString
    }
  }

  /**
   * Splits a template into text and placeholders. `%%` becomes a literal `%`, a `%` not followed by `1`..`9` or `%`
   * stays text. Adjacent text is merged.
   */
  def parts(template: String): Seq[Part] = {
    val result = Vector.newBuilder[Part]
    val text   = new StringBuilder
    var i      = 0 // scalafix:ok
    while (i < template.length) {
      val c = template.charAt(i)
      if (c == '%' && i + 1 < template.length) {
        val n = template.charAt(i + 1)
        if (n == '%') {
          text += '%'
          i += 2
        } else if (n >= '1' && n <= '9') {
          if (text.nonEmpty) {
            result += Part.Text(text.result())
            text.clear()
          }
          result += Part.Arg(n - '0')
          i += 2
        } else {
          text += c
          i += 1
        }
      } else {
        text += c
        i += 1
      }
    }
    if (text.nonEmpty) {
      result += Part.Text(text.result())
    }
    result.result()
  }

  /** Part of a template. */
  enum Part {

    /** Literal text. */
    case Text(text: String)

    /** Placeholder `%n`, `n` counts from 1. */
    case Arg(n: Int)
  }
}
