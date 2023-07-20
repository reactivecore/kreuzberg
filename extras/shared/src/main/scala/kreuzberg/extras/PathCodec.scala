package kreuzberg.extras

/** Helper for encoding/decoding paths. */
trait PathCodec[S] {
  def handles(path: String): Boolean

  def decode(path: String): Option[S]

  def forceDecode(path: String): S = decode(path).getOrElse {
    throw new IllegalStateException("Invalid path")
  }

  def encode(value: S): String
}

object PathCodec {

  /** A Constant path. */
  def const(constantPath: String): PathCodec[Unit] = new PathCodec[Unit] {

    override def handles(path: String): Boolean = path == constantPath

    override def decode(path: String): Option[Unit] = if (path == constantPath) {
      Some(())
    } else {
      None
    }

    override def encode(value: Unit): String = constantPath
  }

  /** A Simple Prefix. */
  def prefix(prefix: String): PathCodec[String] = new PathCodec[String] {

    override def handles(path: String): Boolean = path.startsWith(prefix)

    override def decode(path: String): Option[String] = {
      if (handles(prefix)) {
        Some(path.stripPrefix(prefix))
      } else {
        None
      }
    }

    override def encode(value: String): String = {
      prefix + value
    }
  }

  /** Collects all. */
  def all: PathCodec[String] = new PathCodec[String] {

    override def handles(path: String): Boolean = true

    override def decode(path: String): Option[String] = Some(path)

    override def encode(value: String): String = value
  }
}
