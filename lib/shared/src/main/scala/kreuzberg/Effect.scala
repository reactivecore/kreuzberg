package kreuzberg

import scala.concurrent.{ExecutionContext, Future}

/** Wraps an Effect so that it is lazy */
case class Effect[T](fn: ExecutionContext => Future[T]) {
  def map[U](mapFn: T => U): Effect[U] = {
    Effect { ec =>
      fn(ec).map(mapFn)(ec)
    }
  }
}

object Effect {

  def const[T](value: T): Effect[T] = Effect(_ => Future.successful(value))

  inline def future[T](f: ExecutionContext ?=> Future[T]): Effect[T] = Effect(ec => f(using ec))
}
