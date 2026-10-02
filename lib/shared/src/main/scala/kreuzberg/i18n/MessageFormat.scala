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
}
