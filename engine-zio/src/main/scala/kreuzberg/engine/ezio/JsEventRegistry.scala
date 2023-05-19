package kreuzberg.engine.ezio

import kreuzberg.*
import kreuzberg.dom.*
import kreuzberg.engine.ezio.JsEventRegistry.Canceller
import kreuzberg.engine.ezio.utils.MultiListMap
import zio.stream.ZStream
import zio.{Chunk, Ref, Task, UIO, ZIO}
import scala.util.control.NonFatal

/** Manages JS DOM Events and brings them on ZIO Level and make it also possible for them to disappear. */
class JsEventRegistry[K](
    subscriptions: Ref[MultiListMap[K, Canceller]]
) {

  /** Lift a ScalaJS Event into a ZStream, which is cancellable using the given key. */
  def lift[E](
      key: K,
      target: ScalaJsEventTarget,
      event: JsEvent[E]
  ): ZStream[Any, Throwable, E] = {
    ZStream.asyncZIO { callback =>
//      Logger.debug(s"Registering event ${event.name} on ${key}, target: ${target}")
      val eventListener: scalajs.js.Function1[ScalaJsEvent, Unit] = { e =>
        try {
          val transformed = event.fn(e)
          callback.single(transformed)
        } catch {
          case NonFatal(e) =>
            Logger.warn(s"Exception on JS Event ${key}/${event.name}: $e")
        }
      }

      val canceller = for {
        _ <- ZIO.attempt(target.removeEventListener(event.name, eventListener)).ignoreLogged
        _ <- ZIO.attempt(callback.end).ignoreLogged
      } yield ()

      for {
        _ <- subscriptions.update(_.add(key, canceller))
        _ <- ZIO.attempt {
               target.addEventListener(event.name, eventListener, useCapture = event.capture)
             }.ignoreLogged
      } yield {
        ()
      }
    }
  }

  /** Cancels all associated Streams with this key. */
  def cancel(key: K): UIO[Unit] = {
    for {
      cancellers <- subscriptions.modify(_.remove(key))
//      _           = if (cancellers.nonEmpty) {
//                      Logger.debug(s"Cancelling ${key}")
//                    }
      _          <- ZIO.collectAllDiscard(cancellers)
    } yield {
      ()
    }
  }

  /** Cancel all streams which are not referenced by as key. */
  def cancelUnreferenced(referenced: Set[K]): UIO[Unit] = {
    subscriptions
      .modify { subscriptions =>
        val (alive, toGo) = subscriptions.partitionKeys(key => referenced.contains(key))
        (toGo, alive)
      }
      .flatMap { (toGo: MultiListMap[K, Canceller]) =>
        ZIO.foreachDiscard(toGo.values)(x => x)
      }
  }
}

object JsEventRegistry {
  type Canceller = UIO[Unit]

  def create[T]: UIO[JsEventRegistry[T]] = for {
    state <- Ref.make(MultiListMap[T, Canceller]())
  } yield {
    JsEventRegistry(state)
  }
}
