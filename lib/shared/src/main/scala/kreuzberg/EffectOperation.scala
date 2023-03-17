package kreuzberg

import scala.concurrent.{ExecutionContext, Future}
import zio.{Task}

case class EffectOperation[E, F[_], R](
    fn: E => F[R]
)(implicit val support: EffectSupport[F])

/** Type class for different effect types */
trait EffectSupport[F[_]] {
  val name: String
  /* We can't implement converters here, as we just have provided dependency to ZIO, and this makes ScalaJS crash */
}

object EffectSupport {
  val FutureName = "future"
  val TaskName   = "task"

  implicit def futureSupport: EffectSupport[Future] = new EffectSupport[Future] {
    override val name: String = FutureName
  }

  implicit def zioSupport: EffectSupport[Task] = new EffectSupport[Task] {
    override val name: String = TaskName
  }
}
