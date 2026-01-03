package kreuzberg.extras
import scala.language.implicitConversions

/** Path inside UrlResource. */
case class UrlPath(path: List[String] = Nil) extends AnyVal {
  def startsWith(other: UrlPath): Boolean = path.startsWith(other.path)

  def stripPrefix(other: UrlPath): Option[UrlPath] = Option.when(startsWith(other)) {
    UrlPath(path.drop(other.path.length))
  }

  inline def isEmpty: Boolean = path.isEmpty

  override def toString: String = {
    val sb = StringBuilder()
    printTo(sb)
    sb.result()
  }

  def printTo(sb: StringBuilder): Unit = {
    if (path.isEmpty) {
      sb += '/'
    } else {
      path.foreach { p =>
        sb += '/'
        sb ++= UriHelper.encodePathSegment(p)
      }
    }
  }
}

object UrlPath {
  def decode(path: String): UrlPath = {
    UrlPath(path.split('/').view.filter(_.nonEmpty).map(UriHelper.decodePathSegment).toList)
  }

  implicit def fromString(s: String): UrlPath = {
    decode(s)
  }
}

/**
 * Path + Query + Fragment of an URL
 *
 * Note: path is not url encoded, so keep care do not have illegal characters in it.
 */
case class UrlResource(path: UrlPath = UrlPath(), query: Seq[(String, String)] = Nil, fragment: String = "") {

  /** Returns the query parameters */
  lazy val queryMap: Map[String, String] = query.toMap

  def dropFirstPathPart: Option[(String, UrlResource)] = {
    path.path match {
      case head :: tail =>
        Some(head, copy(path = UrlPath(tail)))
      case _            => None
    }
  }

  def prependPathPart(part: String): UrlResource = {
    copy(
      path = UrlPath(part :: path.path)
    )
  }

  def prependPath(path: UrlPath): UrlResource = {
    copy(
      path = UrlPath(path.path ++ this.path.path)
    )
  }

  def addQuery(queryArgs: Seq[(String, String)]): UrlResource = {
    copy(
      query = query ++ queryArgs
    )
  }

  override def toString: String = str

  def str: String = {
    val sb = StringBuilder()
    path.printTo(sb)
    if (query.nonEmpty) {
      sb += '?'
      var first = true
      query.foreach { case (key, value) =>
        if (!first) {
          sb += '&'
        }
        sb ++= UriHelper.encodeUriComponent(key)
        sb += '='
        sb ++= UriHelper.encodeUriComponent(value)
        first = false;
      }
    }
    if (fragment.nonEmpty) {
      sb += '#'
      sb ++= UriHelper.encodeUriComponent(fragment)
    }
    sb.result()
  }
}

object UrlResource {

  def apply(s: String): UrlResource = {
    val (fragment, rest) = s.lastIndexOf('#') match {
      case -1 => ("", s)
      case n  => (UriHelper.decodeUriComponent(s.drop(n + 1)), s.take(n))
    }
    val (query, path)    = rest.lastIndexOf('?') match {
      case -1 => (Seq.empty, rest)
      case n  => (decodeQuery(rest.drop(n + 1)), rest.take(n))
    }

    UrlResource(UrlPath.decode(path), query, fragment)
  }

  private def decodePath(path: String): List[String] = {
    path.split('/').filter(_.nonEmpty).toList
  }

  private def decodeQuery(query: String): Seq[(String, String)] = {
    query
      .split('&')
      .map { queryPart =>
        queryPart.indexOf('=') match {
          case -1 => UriHelper.decodeUriComponent(queryPart) -> ""
          case n  =>
            val name  = UriHelper.decodeUriComponent(queryPart.take(n))
            val value = UriHelper.decodeUriComponent(queryPart.drop(n + 1))
            (name -> value)
        }
      }
  }

  /** Strip the Query from a path */
  def stripQuery(path: String): String = {
    path.indexOf('?') match {
      case -1 => path
      case n  => path.take(n)
    }
  }
}
