package kreuzberg.miniserver
import kreuzberg.rpc.{Effect, Failure}
import zio.{Task, ZIO}

object ZioEffect {
  implicit object effect extends Effect[Task] {
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
