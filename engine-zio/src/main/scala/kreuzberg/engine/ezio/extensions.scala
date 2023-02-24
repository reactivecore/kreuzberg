package kreuzberg.engine.ezio
import kreuzberg.Logger
import kreuzberg.Logger.isDebug
import kreuzberg.util.Stateful
import zio.{Ref, Task, UIO, ZIO}

type ScalaJsEventTarget = org.scalajs.dom.EventTarget

extension (l: Logger.type) {
  def debugZio(str: => String): Task[Unit] = {
    if (l.isDebug) {
      ZIO.attempt {
        Logger.debug(str)
      }
    } else {
      ZIO.unit
    }
  }

  def warnZio(str: => String): Task[Unit] = {
    ZIO.attempt(
      Logger.warn(str)
    )
  }

  def infoZio(str: => String): Task[Unit] = {
    ZIO.attempt(
      Logger.info(str)
    )
  }
}

extension [S, T](s: Stateful[S, T]) {

  /** Run the stateful operation on a reference. */
  def onRef(ref: Ref[S]): UIO[T] = {
    ref.modify(current => s.fn(current).swap)
  }
}
