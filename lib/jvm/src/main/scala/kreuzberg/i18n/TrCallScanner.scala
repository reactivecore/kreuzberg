package kreuzberg.i18n

import scala.collection.mutable

/** A `tr("key", ...)` call found in source code. */
case class TrCall(key: String, argCount: Int, source: String, line: Int) {
  def position: String = s"${source}:${line}"
}

/**
 * Finds `tr("key", args...)` calls in Scala source code.
 *
 * This is a lightweight lexer, not a parser: it skips comments, string and character literals and counts top level
 * arguments. Calls whose first argument is not a plain string literal are ignored (the `tr` macro rejects them anyway).
 */
object TrCallScanner {

  def scan(content: String, source: String = ""): Seq[TrCall] = {
    val result = mutable.ListBuffer.empty[TrCall]
    var i      = 0 // scalafix:ok
    while (i < content.length) {
      skipNonCode(content, i) match {
        case Some(next) => i = next
        case None       =>
          if (isTrStart(content, i)) {
            parseCall(content, i + 2).foreach { case (key, argCount) =>
              result += TrCall(key, argCount, source, lineOf(content, i))
            }
            i += 2
          } else {
            i += 1
          }
      }
    }
    result.toList
  }

  /** If position `i` starts a comment or literal, returns the position after it. */
  private def skipNonCode(content: String, i: Int): Option[Int] = {
    if (content.startsWith("//", i)) {
      val end = content.indexOf('\n', i)
      Some(if (end < 0) content.length else end)
    } else if (content.startsWith("/*", i)) {
      val end = content.indexOf("*/", i + 2)
      Some(if (end < 0) content.length else end + 2)
    } else if (content.startsWith("\"\"\"", i)) {
      val end = content.indexOf("\"\"\"", i + 3)
      Some(if (end < 0) content.length else end + 3)
    } else if (content.charAt(i) == '"') {
      Some(skipString(content, i))
    } else if (content.charAt(i) == '\'') {
      Some(skipChar(content, i))
    } else {
      None
    }
  }

  /** `tr` as a separate identifier, followed by `(`. */
  private def isTrStart(content: String, i: Int): Boolean = {
    content.startsWith("tr", i) &&
    (i == 0 || !isIdentChar(content.charAt(i - 1))) &&
    !precededByDef(content, i) && {
      val after = skipWhitespace(content, i + 2)
      after < content.length && content.charAt(after) == '('
    }
  }

  private def precededByDef(content: String, i: Int): Boolean = {
    val before = content.substring(0, i).reverse.dropWhile(_.isWhitespace)
    before.startsWith("fed") && (before.length == 3 || !isIdentChar(before.charAt(3)))
  }

  /** Parses the arguments of a call, `start` is after `tr`. Returns key and number of further arguments. */
  private def parseCall(content: String, start: Int): Option[(String, Int)] = {
    val open     = skipWhitespace(content, start)
    val keyStart = skipWhitespace(content, open + 1)
    if (keyStart >= content.length || content.charAt(keyStart) != '"' || content.startsWith("\"\"\"", keyStart)) {
      None
    } else {
      val keyEnd = skipString(content, keyStart)
      val key    = unescape(content.substring(keyStart + 1, keyEnd - 1))
      countArguments(content, keyEnd).map(key -> _)
    }
  }

  /** Counts top level arguments after the key (= top level commas) until the closing parenthesis. */
  private def countArguments(content: String, start: Int): Option[Int] = {
    var depth    = 0     // scalafix:ok
    var commas   = 0     // scalafix:ok
    var i        = start // scalafix:ok
    var finished = false // scalafix:ok
    while (!finished && i < content.length) {
      skipNonCode(content, i) match {
        case Some(next) => i = next
        case None       =>
          content.charAt(i) match {
            case '(' | '[' | '{'               => depth += 1
            case ')' | ']' | '}' if depth == 0 => finished = true
            case ')' | ']' | '}'               => depth -= 1
            case ',' if depth == 0             => commas += 1
            case _                             =>
          }
          i += 1
      }
    }
    Option.when(finished)(commas)
  }

  private def skipString(content: String, start: Int): Int = {
    var i = start + 1 // scalafix:ok
    while (i < content.length && content.charAt(i) != '"' && content.charAt(i) != '\n') {
      if (content.charAt(i) == '\\') {
        i += 1
      }
      i += 1
    }
    math.min(i + 1, content.length)
  }

  /** Skips a character literal; a single quote which does not start one (e.g. Scala 3 quotes) is skipped alone. */
  private def skipChar(content: String, start: Int): Int = {
    if (start + 2 < content.length && content.charAt(start + 1) != '\\' && content.charAt(start + 2) == '\'') {
      start + 3
    } else if (start + 1 < content.length && content.charAt(start + 1) == '\\') {
      val end = content.indexOf('\'', start + 2)
      if (end < 0) start + 1 else end + 1
    } else {
      start + 1
    }
  }

  private def skipWhitespace(content: String, start: Int): Int = {
    var i = start // scalafix:ok
    while (i < content.length && content.charAt(i).isWhitespace) {
      i += 1
    }
    i
  }

  private def unescape(s: String): String = {
    s.replace("\\\"", "\"").replace("\\\\", "\\")
  }

  private def isIdentChar(c: Char): Boolean = c.isLetterOrDigit || c == '_' || c == '$'

  private def lineOf(content: String, pos: Int): Int = {
    content.substring(0, pos).count(_ == '\n') + 1
  }
}
