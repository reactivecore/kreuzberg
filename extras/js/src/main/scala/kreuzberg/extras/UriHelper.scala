package kreuzberg.extras

object UriHelper {
  inline def encodeUriComponent(s: String): String = scalajs.js.URIUtils.encodeURIComponent(s)

  inline def decodeUriComponent(s: String): String = scalajs.js.URIUtils.decodeURIComponent(s)
}
