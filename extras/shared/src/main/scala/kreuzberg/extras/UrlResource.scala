package kreuzberg.extras

/**
 * Path + Query + Fragment of an URL
 *
 * Note: path is not url encoded, so keep care do not have illegal characters in it.
 */
case class UrlResource(str: String = "/") extends AnyVal {

  /** Returns the path */
  def path: String = {
    str.indexOf('?') match {
      case -1 => str
      case n  => str.take(n)
    }
  }

  /** Returns the query without ? */
  def query: String = {
    str.indexOf('?') match {
      case -1 => ""
      case n  =>
        val first = str.drop(n + 1)
        first.indexOf('#') match {
          case -1 => first
          case m  => first.take(m)
        }
    }
  }

  /** Returns the fragment part. */
  def fragment: String = {
    str.indexOf('#') match {
      case -1 => ""
      case n  => UriHelper.decodeUriComponent(str.drop(n + 1))
    }
  }

  /** Returns the query parameters */
  def queryArgs: Map[String, String] = {
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
      .toMap
  }

  override def toString: String = {
    str
  }
}

object UrlResource {

  /** Strip the Query from a path */
  def stripQuery(path: String): String = {
    path.indexOf('?') match {
      case -1 => path
      case n  => path.take(n)
    }
  }

  def encodeWithArgs(path: String, args: Seq[(String, String)] = Nil, fragment: String = ""): UrlResource = {
    val builder = StringBuilder()
    builder ++= path
    if (args.nonEmpty) {
      builder ++= "?"
      var first = true
      args.foreach { case (key, value) =>
        if (!first) {
          builder ++= "&"
        }
        builder ++= UriHelper.encodeUriComponent(key)
        builder ++= "="
        builder ++= UriHelper.encodeUriComponent(value)
        first = false
      }
    }
    if (fragment.nonEmpty) {
      builder ++= "#"
      builder ++= UriHelper.encodeUriComponent(fragment)
    }
    UrlResource(builder.result())
  }
}
