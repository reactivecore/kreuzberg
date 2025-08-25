package kreuzberg.extras

import java.util.UUID
import scala.Tuple.:*
import scala.util.Try

/** Helper for encoding/decoding [[UrlResource]]. */
trait PathCodec[S] {
  def handles(resource: UrlResource): Boolean

  def decode(resource: UrlResource): Option[S]

  def forceDecode(resource: UrlResource): S = decode(resource).getOrElse {
    throw new IllegalStateException("Invalid path")
  }

  def encode(value: S): UrlResource

  def xmap[T](mapFn: S => T, contraMapFn: T => S): PathCodec[T] = PathCodec.XMap(this, mapFn, contraMapFn)
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

  case class XMap[U, T](underlying: PathCodec[U], mapFn: U => T, contraMapFn: T => U) extends PathCodec[T] {
    override def handles(resource: UrlResource): Boolean = underlying.handles(resource)

    override def decode(resource: UrlResource): Option[T] = underlying.decode(resource).map(mapFn)

    override def encode(value: T): UrlResource = underlying.encode(contraMapFn(value))
  }

  /** A Recursive constructed Path. */
  sealed trait RecursivePath[S <: Tuple] extends PathCodec[S] {
    protected def prefixCheck(s: UrlResource): Boolean = true

    def fixed(name: String): RecursivePath[S] = RecursivePath.FixedSubPath(name, this)

    def string: RecursivePath[S :* String] = RecursivePath.PathElement(this, identity, s => Some(s))

    def uuid: RecursivePath[S :* UUID] = {
      RecursivePath.PathElement(this, s => s.toString, s => Try(UUID.fromString(s)).toOption)
    }

    def int: RecursivePath[S :* Int] = {
      RecursivePath.PathElement(this, s => s.toString, s => s.toIntOption)
    }

    def long: RecursivePath[S :* Long] = {
      RecursivePath.PathElement(this, s => s.toString, s => s.toLongOption)
    }

    def boolean: RecursivePath[S :* Boolean] = {
      RecursivePath.PathElement(this, s => s.toString, s => s.toBooleanOption)
    }

    /** When only using one value, you can convert it into a single path codec. */
    def one[X](using ev: S => Tuple1[X]): PathCodec[X] = xmap(
      x => x.asInstanceOf[Tuple1[X]]._1,
      x => Tuple1(x).asInstanceOf[S]
    )
  }

  /** Builds a recursive path */
  def recursive(prefix: String): RecursivePath.Start = RecursivePath.Start(prefix)

  object RecursivePath {
    case class Start(prefix: String) extends RecursivePath[EmptyTuple] {
      override def handles(resource: UrlResource): Boolean = resource.path == prefix

      override protected def prefixCheck(resource: UrlResource): Boolean = {
        resource.str.startsWith(prefix)
      }

      override def decode(resource: UrlResource): Option[EmptyTuple] = Option.when(handles(resource)) {
        EmptyTuple
      }

      override def encode(value: EmptyTuple): UrlResource = UrlResource(prefix)
    }

    case class PathElement[T, P <: Tuple](parent: RecursivePath[P], encoder: T => String, decoder: String => Option[T])
        extends RecursivePath[P :* T] {
      override def handles(resource: UrlResource): Boolean = {
        parent.prefixCheck(resource) && decode(resource).isDefined
      }

      override protected def prefixCheck(s: UrlResource): Boolean = {
        parent.prefixCheck(s)
      }

      override def decode(resource: UrlResource): Option[Tuple.Append[P, T]] = {
        for {
          (part, parentResource) <- resource.dropSubPath
          decoded                <- decoder(part)
          decodedParent          <- parent.decode(parentResource)
        } yield {
          decodedParent :* decoded
        }
      }

      override def encode(value: P :* T): UrlResource = {
        parent.encode(value.init.asInstanceOf[P]).subPath(encoder(value.last.asInstanceOf[T]))
      }
    }

    case class FixedSubPath[P <: Tuple](name: String, parent: RecursivePath[P]) extends RecursivePath[P] {
      override def handles(resource: UrlResource): Boolean = {
        parent.prefixCheck(resource) && decode(resource).isDefined
      }

      override def decode(resource: UrlResource): Option[P] = {
        for {
          (part, parentResource) <- resource.dropSubPath
          if part == name
          result                 <- parent.decode(parentResource)
        } yield result
      }

      override def encode(value: P): UrlResource = {
        parent.encode(value).subPath(name)
      }
    }
  }
}
