package kreuzberg.engine.ezio
import kreuzberg.Logger
import kreuzberg.Logger.isDebug
import zio.{Fiber, Ref, Task, Trace, UIO, ZIO}

import scala.util.control.NonFatal

type ScalaJsEventTarget = org.scalajs.dom.EventTarget

extension (l: Logger.type) {
  def traceZio(str: => String): Task[Unit] = {
    if (l.isTrace) {
      ZIO.attempt(
        Logger.trace(str)
      )
    } else {
      ZIO.unit
    }
  }

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

extension [R, E, A](in: ZIO[R, E, A]) {
  def forkZioLogged(name: String)(implicit t: Trace): zio.URIO[R, Fiber.Runtime[Any, A]] = {
    in.tapErrorTrace { (e, st) =>
      Logger.warnZio(s"ZIO ${name} failed, ${e}, st: ${st.prettyPrint}")
    }.fork
  }
}
