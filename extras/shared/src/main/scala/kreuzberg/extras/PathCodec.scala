package kreuzberg.extras

/** Helper for encoding/decoding [[UrlResource]]. */
trait PathCodec[S] {
  def handles(resource: UrlResource): Boolean

  def decode(resource: UrlResource): Option[S]

  def forceDecode(resource: UrlResource): S = decode(resource).getOrElse {
    throw new IllegalStateException("Invalid path")
  }

  def encode(value: S): UrlResource
}

object PathCodec {

  /** A Constant path. */
  def const(path: String): PathCodec[Unit] = new PathCodec[Unit] {

    override def handles(resource: UrlResource): Boolean = resource.path == path

    override def decode(resource: UrlResource): Option[Unit] = if (path == resource.path) {
      Some(())
    } else {
      None
    }

    override def encode(value: Unit): UrlResource = UrlResource(path)
  }

  def constWithQueryParams(path: String, params: String*): PathCodec[Seq[String]] = new PathCodec[Seq[String]] {
    override def handles(resource: UrlResource): Boolean = {
      resource.path == path && {
        val queryArgs = resource.queryArgs
        params.forall(p => queryArgs.contains(p))
      }
    }

    override def decode(resource: UrlResource): Option[Seq[String]] = {
      if (resource.path != path) {
        None
      } else {
        val builder   = Seq.newBuilder[String]
        val queryArgs = resource.queryArgs
        val it        = params.iterator
        while (it.hasNext) {
          queryArgs.get(it.next()) match {
            case None    => return None
            case Some(v) => builder += v
          }
        }
        Some(builder.result())
      }
    }

    override def encode(value: Seq[String]): UrlResource = {
      UrlResource.encodeWithArgs(path, params.zip(value))
    }
  }

  /** A Simple Prefix. */
  def prefix(prefix: String): PathCodec[String] = new PathCodec[String] {

    override def handles(resource: UrlResource): Boolean = resource.path.startsWith(prefix)

    override def decode(resource: UrlResource): Option[String] = {
      if (handles(resource)) {
        Some(resource.path.stripPrefix(prefix))
      } else {
        None
      }
    }

    override def encode(value: String): UrlResource = {
      UrlResource(prefix + value)
    }
  }

  /** Collects all. */
  def all: PathCodec[UrlResource] = new PathCodec[UrlResource] {

    override def handles(path: UrlResource): Boolean = true

    override def decode(resource: UrlResource): Option[UrlResource] = Some(resource)

    override def encode(value: UrlResource): UrlResource = value
  }
}
