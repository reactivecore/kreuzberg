package kreuzberg

import scala.concurrent.{ExecutionContext, Future}

case class EffectOperation[E, F](
    fn: (E, ExecutionContext) => Future[F]
)
