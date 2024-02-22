package kreuzberg

import scala.concurrent.{ExecutionContext, Future}

/** Wraps an Effect. */
sealed trait Effect[+T] {
  def map[U](f: T => U): Effect[U]
}

object Effect {
  case class LazyFuture[T](fn: ExecutionContext => Future[T]) extends Effect[T] {
    override def map[U](f: T => U): Effect[U] = {
      LazyFuture { ec =>
        fn(ec).map(f)(ec)
      }
    }
  }

  case class Const[T](value: T) extends Effect[T] {
    override def map[U](f: T => U): Effect[U] = Const(f(value))
  }

  def const[T](value: T): Const[T] = Const(value)

  def future[T](f: ExecutionContext => Future[T]): LazyFuture[T] = LazyFuture(f)
}
