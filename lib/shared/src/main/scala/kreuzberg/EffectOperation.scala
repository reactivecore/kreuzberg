package kreuzberg

import scala.concurrent.{ExecutionContext, Future}
import zio.Task

case class EffectOperation[E, F[_], R](
    fn: E => F[R]
)(implicit val support: EffectSupport[F]) {

  def map[R2](fn: R => R2): EffectOperation[E, F, R2] = support.map(this, fn)

  def contraMap[D](fn: D => E): EffectOperation[D, F, R] = EffectOperation(this.fn.compose(fn))
}

/** Type class for different effect types */
trait EffectSupport[F[_]] {
  val name: String

  def map[E, R1, R2](op: EffectOperation[E, F, R1], fn: R1 => R2): EffectOperation[E, F, R2]

  /* We can't implement converters here, as we just have provided dependency to ZIO, and this makes ScalaJS crash */
}

object EffectSupport {
  val FutureName = "future"
  val TaskName   = "task"

  implicit def futureSupport: EffectSupport[Future] = new EffectSupport[Future] {
    override val name: String = FutureName

    override def map[E, R1, R2](op: EffectOperation[E, Future, R1], fn: R1 => R2): EffectOperation[E, Future, R2] = {
      import scala.concurrent.ExecutionContext.parasitic
      EffectOperation { in =>
        op.fn(in).map(fn)(parasitic)
      }
    }
  }

  implicit def zioSupport: EffectSupport[Task] = new EffectSupport[Task] {
    override val name: String = TaskName

    override def map[E, R1, R2](op: EffectOperation[E, Task, R1], fn: R1 => R2): EffectOperation[E, Task, R2] = {
      EffectOperation { in =>
        op.fn(in).map(fn)
      }
    }
  }
}
