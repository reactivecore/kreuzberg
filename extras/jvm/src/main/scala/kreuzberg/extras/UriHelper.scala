package kreuzberg.extras

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets

object UriHelper {
  inline def encodeUriComponent(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

  inline def decodeUriComponent(s: String): String = URLDecoder.decode(s, StandardCharsets.UTF_8)
}
