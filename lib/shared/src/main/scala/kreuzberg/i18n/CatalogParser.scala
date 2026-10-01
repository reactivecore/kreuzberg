package kreuzberg.i18n

import scala.collection.mutable

/**
 * Parser for `.rctr` translation catalogs.
 *
 * {{{
 * catalog := '@' 'locale' string  entry*
 * entry   := key ':' string ';'?
 * key     := ident ('.' ident)*
 * }}}
 *
 * Comments: `// ...` and `/* ... */`. String escapes: `\\`, `\"`, `\n`, `\t`, `\r`, `\u{XXXX}`.
 */
object CatalogParser {

  def parse(content: String, source: String = ""): Either[CatalogError, Catalog] = {
    val state = new State(content, source)
    try {
      Right(state.catalog())
    } catch {
      case e: ParseException => Left(e.error)
    }
  }

  private class ParseException(val error: CatalogError) extends RuntimeException(error.toString)

  private class State(content: String, source: String) {
    private var pos  = 0 // scalafix:ok
    private var line = 1 // scalafix:ok

    def catalog(): Catalog = {
      skipWhitespace()
      expect('@')
      val directive = ident()
      if (directive != "locale") {
        fail(s"Expected @locale, got @${directive}")
      }
      skipWhitespace()
      val locale    = string()
      val entries   = mutable.LinkedHashMap.empty[String, String]
      skipWhitespace()
      while (!atEnd) {
        val keyLine = line
        val k       = key()
        skipWhitespace()
        expect(':')
        skipWhitespace()
        val value   = string()
        if (entries.contains(k)) {
          throw ParseException(CatalogError(source, keyLine, s"Duplicate key ${k}"))
        }
        entries.update(k, value)
        skipWhitespace()
        if (peek.contains(';')) {
          advance()
          skipWhitespace()
        }
      }
      Catalog(locale, entries.toMap, source)
    }

    private def atEnd: Boolean = pos >= content.length

    private def peek: Option[Char] = if (atEnd) None else Some(content.charAt(pos))

    private def peekAt(offset: Int): Option[Char] = {
      val p = pos + offset
      if (p >= content.length) None else Some(content.charAt(p))
    }

    private def advance(): Char = {
      val c = content.charAt(pos)
      pos += 1
      if (c == '\n') {
        line += 1
      }
      c
    }

    private def fail(message: String): Nothing = {
      throw ParseException(CatalogError(source, line, message))
    }

    private def expect(c: Char): Unit = {
      peek match {
        case Some(`c`) => advance()
        case Some(o)   => fail(s"Expected '${c}', got '${o}'")
        case None      => fail(s"Expected '${c}', got end of input")
      }
    }

    private def skipWhitespace(): Unit = {
      var continue = true // scalafix:ok
      while (continue && !atEnd) {
        peek match {
          case Some(c) if c.isWhitespace            => advance()
          case Some('/') if peekAt(1).contains('/') =>
            while (!atEnd && !peek.contains('\n')) {
              advance()
            }
          case Some('/') if peekAt(1).contains('*') =>
            val startLine = line
            advance()
            advance()
            while (!atEnd && !(peek.contains('*') && peekAt(1).contains('/'))) {
              advance()
            }
            if (atEnd) {
              throw ParseException(CatalogError(source, startLine, "Unterminated comment"))
            }
            advance()
            advance()
          case _                                    => continue = false
        }
      }
    }

    private def ident(): String = {
      val start = pos
      peek match {
        case Some(c) if c.isLetter || c == '_' => advance()
        case Some(o)                           => fail(s"Expected identifier, got '${o}'")
        case None                              => fail("Expected identifier, got end of input")
      }
      while (peek.exists(c => c.isLetterOrDigit || c == '_')) {
        advance()
      }
      content.substring(start, pos)
    }

    private def key(): String = {
      val first = ident()
      val rest  = mutable.ListBuffer.empty[String]
      while (peek.contains('.')) {
        advance()
        rest += ident()
      }
      (first :: rest.toList).mkString(".")
    }

    private def string(): String = {
      expect('"')
      val sb   = new StringBuilder
      var done = false // scalafix:ok
      while (!done) {
        peek match {
          case None | Some('\n') => fail("Unterminated string")
          case Some('"')         =>
            advance()
            done = true
          case Some('\\')        =>
            advance()
            escape(sb)
          case Some(_)           =>
            sb += advance()
        }
      }
      sb.result()
    }

    private def escape(sb: StringBuilder): Unit = {
      peek match {
        case Some('\\') => sb += advance()
        case Some('"')  => sb += advance()
        case Some('n')  =>
          advance()
          sb += '\n'
        case Some('t')  =>
          advance()
          sb += '\t'
        case Some('r')  =>
          advance()
          sb += '\r'
        case Some('u')  =>
          advance()
          expect('{')
          val start = pos
          while (peek.exists(c => Character.digit(c, 16) >= 0)) {
            advance()
          }
          val hex   = content.substring(start, pos)
          if (hex.isEmpty || hex.length > 6) {
            fail(s"Invalid unicode escape \\u{${hex}}")
          }
          expect('}')
          val code  = Integer.parseInt(hex, 16)
          if (!Character.isValidCodePoint(code) || (code >= 0xd800 && code <= 0xdfff)) {
            fail(s"Invalid unicode scalar value \\u{${hex}}")
          }
          sb.appendAll(Character.toChars(code))
        case Some(o)    => fail(s"Invalid escape \\${o}")
        case None       => fail("Unterminated string")
      }
    }
  }
}
