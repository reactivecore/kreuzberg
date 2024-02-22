package kreuzberg.rpc
import io.circe.{Decoder, Encoder, Json}

import scala.concurrent.{ExecutionContext, Future}
import zio.{Task, ZIO}

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

  // Note: Only available for clients which do have ZIO Imported
  implicit def zioEffect: EffectSupport[Task] = new EffectSupport[Task] {
    override def failure[A](failure: Failure): Task[A] = {
      ZIO.fail(failure)
    }

    override def success[A](value: A): Task[A] = {
      ZIO.succeed(value)
    }

    override def flatMap[A, B](in: Task[A])(f: A => Task[B]): Task[B] = {
      in.flatMap(f)
    }

    override def map[A, B](in: Task[A])(f: A => B): Task[B] = {
      in.map(f)
    }
  }
}
