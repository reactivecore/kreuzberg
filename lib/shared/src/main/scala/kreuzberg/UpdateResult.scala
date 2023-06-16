package kreuzberg

/** Result of an update operation of a component. */
sealed trait UpdateResult

object UpdateResult {

  /** Just (re)build the component (default) */
  case class Build(assembly: Assembly) extends UpdateResult

  /** Prepend the inner HTML with some code. */
  case class Prepend(assembly: Assembly) extends UpdateResult

  /** Append the inner HTML with some code. */
  case class Append(assembly: Assembly) extends UpdateResult
}
