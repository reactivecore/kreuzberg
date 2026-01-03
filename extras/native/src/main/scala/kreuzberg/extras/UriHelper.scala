package kreuzberg.extras

import java.net.{URLDecoder, URLEncoder}

object UriHelper {
  // Scala Native doesn't know the Charset here
  inline def encodeUriComponent(s: String): String = URLEncoder.encode(s, "UTF-8")

  inline def decodeUriComponent(s: String): String = URLDecoder.decode(s, "UTF-8")

  def encodePathSegment(s: String): String = {
    encodeUriComponent(s).replace("+", "%20")
  }

  def decodePathSegment(s: String): String = {
    decodeUriComponent(s)
  }
}
