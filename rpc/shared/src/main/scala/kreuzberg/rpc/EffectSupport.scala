package kreuzberg.rpc
import io.circe.{Decoder, Encoder, Json}

import scala.concurrent.{ExecutionContext, Future}

type Id[T] = T

/** Defines necessary properties of the used effect */
trait EffectSupport[F[_]] {

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

  def wrapFlatMap[A](in: Either[Failure, A])(f: A => F[Json]): F[Json] = {
    in match {
      case Left(f)   => failure(f)
      case Right(ok) => f(ok)
    }
  }

  /** Defines a map expression. */
  def map[A, B](in: F[A])(f: A => B): F[B]

  // Helpers to make implementation of Macros easier.

  def decodeResponse[A](in: F[Response])(implicit decoder: Decoder[A]): F[A] = {
    flatMap(in) { transport =>
      decoder.decodeJson(transport.json) match {
        case Left(bad) => failure(Failure.fromDecodingFailure(bad))
        case Right(ok) => success(ok)
      }
    }
  }

  def encodeResponse[R](in: F[R])(using encoder: Encoder[R]): F[Response] = {
    map(in) { resultValue =>
      Response.build(resultValue)
    }
  }
}

object EffectSupport {
  implicit def futureSupport(implicit ec: ExecutionContext): EffectSupport[Future] = new EffectSupport[Future] {

    override def success[A](value: A): Future[A] = {
      Future.successful(value)
    }

    override def failure[A](failure: Failure): Future[A] = Future.failed(failure)

    override def flatMap[A, B](in: Future[A])(f: A => Future[B]): Future[B] = in.flatMap(f)

    override def map[A, B](in: Future[A])(f: A => B): Future[B] = in.map(f)
  }

  implicit object idSupport extends EffectSupport[Id] {
    override def failure[A](failure: Failure): Id[A] = throw failure

    override def success[A](value: A): Id[A] = value

    override def flatMap[A, B](in: Id[A])(f: A => Id[B]): Id[B] = f(in)

    override def map[A, B](in: Id[A])(f: A => B): Id[B] = f(in)
  }
}
