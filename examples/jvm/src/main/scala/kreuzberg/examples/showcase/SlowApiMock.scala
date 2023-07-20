package kreuzberg.examples.showcase

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

/** Emulates a slow API (JVM) */
object SlowApiMock {

  def timer[T](duration: FiniteDuration, result: T): Future[T] = {
    Future {
      Thread.sleep(duration.toMillis)
      result
    }
  }
}
