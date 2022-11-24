package kreuzberg

import kreuzberg.util.Stateful

/**
 * A Type class providing something using Assembly State. Used for dependency injecting shared models.
 */
trait Provider[T] {
  def provide: Stateful[AssemblyState, T]
}
