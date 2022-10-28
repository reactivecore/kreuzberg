package kreuzberg.util

// TODO: Use Cats' State
case class Stateful[S, +T](fn: S => (S, T)) {
  self =>

  def flatMap[U](fn: T => Stateful[S, U]): Stateful[S, U] = Stateful { s0 =>
    val (s1, v1) = self.fn(s0)
    fn(v1).fn(s1)
  }

  def map[U](fn: T => U): Stateful[S, U] = Stateful { s0 =>
    val (s1, v1) = self.fn(s0)
    (s1, fn(v1))
  }

  def apply(state: S): (S, T) = fn(state)
}

object Stateful {
  def pure[S, T](value: T): Stateful[S, T] = Stateful(s => (s, value))

  def get[S, T](f: S => T): Stateful[S, T] = Stateful(s => (s, f(s)))

  /** Just modify state. */
  def modify[S](f: S => S): Stateful[S, Unit] = Stateful(s => (f(s), ()))

  def accumulate[S, A, B](in: Seq[A])(f: A => Stateful[S, B]): Stateful[S, Vector[B]] = {
    Stateful { state =>
      accumulate(in, state) { case (state, value) =>
        f(value).fn(state)
      }
    }
  }

  def accumulate[S, A, B](in: Seq[A], s0: S)(f: (S, A) => (S, B)): (S, Vector[B]) = {
    var current = s0
    val builder = Vector.newBuilder[B]
    in.foreach { value =>
      val (nextState, result) = f(current, value)
      builder += result
      current = nextState
    }
    (current, builder.result())
  }
}
