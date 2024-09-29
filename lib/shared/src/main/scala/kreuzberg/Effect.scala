package kreuzberg

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

/** Wraps an Effect so that it is lazy */
case class Effect[T](fn: ExecutionContext => Future[T]) {
  def map[U](mapFn: T => U): Effect[U] = {
    Effect { ec =>
      fn(ec).map(mapFn)(ec)
    }
  }

  /** Run to a future. */
  def run()(using ec: ExecutionContext): Future[T] = fn(ec)

  /** Run, but returns a try. */
  def runToTry()(using ec: ExecutionContext): Future[Try[T]] = run().transform { x =>
    Success(x)
  }

  /** Run and handle the result. */
  def runAndHandle(f: Try[T] => Unit)(using ec: ExecutionContext): Unit = runToTry().foreach(f)
}

object Effect {

  def const[T](value: T): Effect[T] = Effect(_ => Future.successful(value))

  inline def future[T](f: ExecutionContext ?=> Future[T]): Effect[T] = Effect(ec => f(using ec))
}
