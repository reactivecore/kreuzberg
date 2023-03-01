package kreuzberg.rpc
import scala.concurrent.{ExecutionContext, Future}

/** Defines necessary properties of the used effect */
trait Effect[F[_]] {

  /** Wraps a codec error into an Effect. */
  def failure[A](failure: Failure): F[A]

  /** Wraps a success into an Effect. */
  def success[A](value: A): F[A]

  /** Wrap an either into an Effect. */
  def wrap[A](input: Either[Failure, A]): F[A] = {
    input match {
      case Left(f)   => failure(f)
      case Right(ok) => success(ok)
    }
  }

  /** Defines a flatMap expression. */
  def flatMap[A, B](in: F[A])(f: A => F[B]): F[B]

  def wrapFlatMap[A, B](in: Either[Failure, A])(f: A => F[B]): F[B] = {
    in match {
      case Left(f)   => failure(f)
      case Right(ok) => f(ok)
    }
  }

  /** Defines a map expression. */
  def map[A, B](in: F[A])(f: A => B): F[B]

  def decodeResult[A, T](in: F[T])(implicit codec: Codec[A, T]): F[A] = {
    flatMap(in) { transport =>
      codec.decode(transport) match {
        case Left(bad) => failure(bad)
        case Right(ok) => success(ok)
      }
    }
  }

  def encodeResult[R, T](in: F[R])(implicit codec: Codec[R, T]): F[T] = {
    map(in) { resultValue =>
      codec.encode(resultValue)
    }
  }
}

object Effect {
  implicit def futureSupport(implicit ec: ExecutionContext): Effect[Future] = new Effect[Future] {

    override def success[A](value: A): Future[A] = {
      Future.successful(value)
    }

    override def failure[A](failure: Failure): Future[A] = Future.failed(failure)

    override def flatMap[A, B](in: Future[A])(f: A => Future[B]): Future[B] = in.flatMap(f)

    override def map[A, B](in: Future[A])(f: A => B): Future[B] = in.map(f)
  }
}