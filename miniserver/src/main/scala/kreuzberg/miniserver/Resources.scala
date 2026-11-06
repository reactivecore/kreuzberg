package kreuzberg.miniserver

import java.nio.charset.StandardCharsets
import scala.util.Using

/** Reads class path resources. */
private[miniserver] object Resources {

  /** Reads a UTF-8 resource, `None` if it does not exist. */
  def readUtf8(classLoader: ClassLoader, path: String): Option[String] = {
    Option(classLoader.getResourceAsStream(path)).map { stream =>
      Using.resource(stream)(s => new String(s.readAllBytes(), StandardCharsets.UTF_8))
    }
  }
}
