package kreuzberg.extras

import java.util.UUID
import scala.Tuple.:*
import scala.concurrent.Future

/** Helper for encoding/decoding [[UrlResource]]. */
trait PathCodec[S] {

  /** Returns true if this path is responsible for this UrlResource. */
  final def handles(url: UrlResource): Boolean = {
    handlesPrefix(url).exists(_.path.isEmpty)
  }

  /** Returns the remainder if this path handles the prefix of this resource. */
  def handlesPrefix(url: UrlResource): Option[UrlResource]

  /** Decode an [[UrlResource]] */
  final def decode(url: UrlResource): Result[S] = for {
    (result, remainder) <- decodePrefix(url)
    _                   <- (if (remainder.path.isEmpty) {
                              Right(())
                            } else {
                              Left(Error.NestedPathError)
                            })
  } yield {
    result
  }

  /**
   * Decode the prefix of an URL Resource, returns decoded value and remainder.
   */
  def decodePrefix(url: UrlResource): Result[(S, UrlResource)]

  /** Forces decoding of this Resource. */
  def decodeToFuture(url: UrlResource): Future[S] = decode(url) match {
    case Left(error) => Future.failed(Error.PathCodecException(error))
    case Right(ok)   => Future.successful(ok)
  }

  /** Encode the value into an UrlResource. */
  final def encode(value: S): UrlResource = {
    encodeToSuffix(value, UrlResource())
  }

  /** Encode a value to the left part of the suffix. */
  protected def encodeToSuffix(value: S, suffix: UrlResource): UrlResource

  def xmap[T](mapFn: S => T, contraMapFn: T => S): PathCodec[T] = PathCodec.XMap(this, mapFn, contraMapFn)
}

object PathCodec {

  /** A Constant path. */
  def const(path: UrlPath): PathCodec[Unit] = new PathCodec[Unit] {

    override def handlesPrefix(url: UrlResource): Option[UrlResource] = {
      url.path.stripPrefix(path).map { remainder =>
        url.copy(path = remainder)
      }
    }

    override def decodePrefix(url: UrlResource): Result[(Unit, UrlResource)] = {
      handlesPrefix(url) match {
        case Some(remainder) => Right(((), remainder))
        case None            => Left(Error.BadPathError(url.path, path))
      }
    }

    override protected def encodeToSuffix(value: Unit, suffix: UrlResource): UrlResource = {
      suffix.prependPath(path)
    }
  }

  /** Collects all. */
  def all: PathCodec[UrlResource] = new PathCodec[UrlResource] {

    override def handlesPrefix(url: UrlResource): Option[UrlResource] = {
      Some(url.copy(path = UrlPath()))
    }

    override def decodePrefix(url: UrlResource): Result[(UrlResource, UrlResource)] = {
      Right(url, url.copy(path = UrlPath()))
    }

    override protected def encodeToSuffix(value: UrlResource, suffix: UrlResource): UrlResource = {
      value
    }
  }

  case class XMap[U, T](underlying: PathCodec[U], mapFn: U => T, contraMapFn: T => U) extends PathCodec[T] {
    override def handlesPrefix(url: UrlResource): Option[UrlResource] = {
      underlying.handlesPrefix(url)
    }

    override def decodePrefix(url: UrlResource): Result[(T, UrlResource)] = {
      underlying.decodePrefix(url).map { case (result, remainder) =>
        mapFn(result) -> remainder
      }
    }

    override protected def encodeToSuffix(value: T, suffix: UrlResource): UrlResource = {
      underlying.encodeToSuffix(contraMapFn(value), suffix)
    }
  }

  /** A Recursive constructed Path. */
  sealed trait RecursivePath[S <: Tuple] extends PathCodec[S] {
    def fixed(name: String): RecursivePath[S] = RecursivePath.FixedSubPath(name, this)

    def string: RecursivePath[S :* String] = part[String]

    def uuid: RecursivePath[S :* UUID] = part[UUID]

    def int: RecursivePath[S :* Int] = part[Int]

    def long: RecursivePath[S :* Long] = part[Long]

    def boolean: RecursivePath[S :* Boolean] = part[Boolean]

    def part[T](using Codec[T, String]): RecursivePath[S :* T] = RecursivePath.PathElement(this)

    /** Decodes a query parameter */
    def query[T](key: String)(using Codec[T, String]): RecursivePath[S :* T] = RecursivePath.QueryParam(key, this)

    /** When only using one value, you can convert it into a single path codec. */
    def one[X](using ev: S =:= Tuple1[X]): PathCodec[X] = xmap(
      x => x.asInstanceOf[Tuple1[X]]._1,
      x => Tuple1(x).asInstanceOf[S]
    )

    def unit[X](using ev: S =:= EmptyTuple): PathCodec[Unit] = xmap(
      _ => (),
      _ => EmptyTuple.asInstanceOf[S]
    )
  }

  /** Builds a recursive path */
  def recursive(prefix: String): RecursivePath.Start = RecursivePath.Start(UrlPath.decode(prefix))

  object RecursivePath {
    case class Wrapped[T](parent: PathCodec[T]) extends RecursivePath[Tuple1[T]] {
      override def handlesPrefix(url: UrlResource): Option[UrlResource] = parent.handlesPrefix(url)

      override def decodePrefix(url: UrlResource): Result[(Tuple1[T], UrlResource)] =
        parent.decodePrefix(url).map { case (result, remainder) =>
          (Tuple1(result), remainder)
        }

      override protected def encodeToSuffix(value: Tuple1[T], suffix: UrlResource): UrlResource = {
        parent.encodeToSuffix(value._1, suffix)
      }
    }

    case class Start(prefix: UrlPath) extends RecursivePath[EmptyTuple] {

      override def handlesPrefix(url: UrlResource): Option[UrlResource] = {
        url.path.stripPrefix(prefix).map { remainder =>
          url.copy(path = remainder)
        }
      }

      override def decodePrefix(url: UrlResource): Result[(EmptyTuple, UrlResource)] = {
        handlesPrefix(url) match {
          case Some(remainder) => Right((EmptyTuple, remainder))
          case None            => Left(Error.BadPathError(url.path, prefix))
        }
      }

      override protected def encodeToSuffix(value: EmptyTuple, suffix: UrlResource): UrlResource = {
        suffix.prependPath(prefix)
      }
    }

    case class PathElement[T, P <: Tuple](parent: RecursivePath[P])(using codec: Codec[T, String])
        extends RecursivePath[P :* T] {
      override def handlesPrefix(url: UrlResource): Option[UrlResource] = {
        for {
          parentRemainder <- parent.handlesPrefix(url)
          (_, remainder)  <- parentRemainder.dropFirstPathPart
        } yield {
          remainder
        }
      }

      override def decodePrefix(url: UrlResource): Result[(P :* T, UrlResource)] = {
        for {
          (decodedParent, remainder) <- parent.decodePrefix(url)
          (subPath, rest)            <- remainder.dropFirstPathPart.toRight(Error.MissingNestedPathError)
          decoded                    <- codec.decode(subPath)
        } yield {
          (decodedParent :* decoded, rest)
        }
      }

      override protected def encodeToSuffix(value: P :* T, suffix: UrlResource): UrlResource = {
        val newPath = suffix.prependPathPart(codec.encode(value.last.asInstanceOf[T]))
        parent.encodeToSuffix(value.init.asInstanceOf[P], newPath)
      }
    }

    case class QueryParam[T, P <: Tuple](key: String, parent: RecursivePath[P])(using codec: Codec[T, String])
        extends RecursivePath[P :* T] {

      override def handlesPrefix(url: UrlResource): Option[UrlResource] = {
        parent.handlesPrefix(url)
      }

      override def decodePrefix(url: UrlResource): Result[(P :* T, UrlResource)] = {
        for {
          value               <- url.queryMap.get(key).toRight(Error.MissingQueryParameter(key))
          decoded             <- codec.decode(value)
          (prefix, remainder) <- parent.decodePrefix(url)
        } yield {
          ((prefix :* decoded), remainder)
        }
      }

      override protected def encodeToSuffix(value: P :* T, suffix: UrlResource): UrlResource = {
        val updated = suffix.copy(
          query = (key -> codec.encode(value.last.asInstanceOf[T])) +: suffix.query
        )
        parent.encodeToSuffix(value.init.asInstanceOf[P], updated)
      }
    }

    case class FixedSubPath[P <: Tuple](part: String, parent: RecursivePath[P]) extends RecursivePath[P] {
      override def handlesPrefix(url: UrlResource): Option[UrlResource] = {
        for {
          remainder     <- parent.handlesPrefix(url)
          (first, rest) <- remainder.dropFirstPathPart
          if first == part
        } yield {
          rest
        }
      }

      override def decodePrefix(url: UrlResource): Result[(P, UrlResource)] = {
        for {
          (decodedParent, parentRest) <- parent.decodePrefix(url)
          (subPath, rest)             <- parentRest.dropFirstPathPart.toRight(Error.MissingNestedPathError)
          _                           <- Either.cond(subPath == part, (), Error.BadPathElementError(subPath, part))
        } yield {
          (decodedParent, rest)
        }
      }

      override protected def encodeToSuffix(value: P, suffix: UrlResource): UrlResource = {
        parent.encodeToSuffix(value, suffix.prependPathPart(part))
      }
    }
  }
}
