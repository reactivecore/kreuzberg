package kreuzberg.examples.showcase

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration

/** Emulates a slow API (JS) */
object SlowApiMock {

  def timer[T](duration: FiniteDuration, result: T): Future[T] = {
    val promise = Promise[T]
    scalajs.js.timers.setTimeout(duration) {
      promise.success(result)
    }
    promise.future
  }
}
