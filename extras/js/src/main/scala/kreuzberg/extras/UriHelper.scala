package kreuzberg.extras

object UriHelper {
  inline def encodeUriComponent(s: String): String = {
    scalajs.js.URIUtils.encodeURIComponent(s)
  }

  inline def decodeUriComponent(s: String): String = {
    scalajs.js.URIUtils.decodeURIComponent(s)
  }

  def encodePathSegment(s: String): String = {
    scalajs.js.URIUtils.encodeURIComponent(s).replace("+", "%20")
  }

  def decodePathSegment(s: String): String = {
    scalajs.js.URIUtils.decodeURIComponent(s)
  }
}
