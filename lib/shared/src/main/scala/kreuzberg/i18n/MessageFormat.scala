package kreuzberg.i18n

/** Formats messages with positional placeholders `%1`..`%9`; `%%` is a literal `%`. */
object MessageFormat {

  /** Substitutes placeholders. A placeholder without argument is substituted with nothing. */
  def format(template: String, args: Seq[Any]): String = {
    if (template.indexOf('%') < 0) {
      template
    } else {
      val sb = new StringBuilder
      var i  = 0 // scalafix:ok
      while (i < template.length) {
        val c = template.charAt(i)
        if (c == '%' && i + 1 < template.length) {
          val n = template.charAt(i + 1)
          if (n == '%') {
            sb += '%'
            i += 2
          } else if (n >= '1' && n <= '9') {
            val idx = n - '1'
            if (idx < args.size) {
              sb ++= String.valueOf(args(idx))
            }
            i += 2
          } else {
            sb += c
            i += 1
          }
        } else {
          sb += c
          i += 1
        }
      }
      sb.result()
    }
  }

  /** Returns the placeholder numbers used in a template. */
  def placeholders(template: String): Set[Int] = {
    PlaceholderRegex.findAllMatchIn(template).collect { case m if m.group(1) != "%" => m.group(1).toInt }.toSet
  }

  /** Returns the number of arguments a template expects (highest placeholder). */
  def arity(template: String): Int = {
    placeholders(template).maxOption.getOrElse(0)
  }

  private val PlaceholderRegex = "%([1-9%])".r
}
