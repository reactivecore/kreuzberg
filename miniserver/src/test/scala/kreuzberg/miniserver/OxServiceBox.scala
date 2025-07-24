package kreuzberg.miniserver

import org.slf4j.{Logger, LoggerFactory}
import ox.*

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Try

/** Wraps an Ox service (like [[MiniServer]]) to be used in Testcases with remote start and stop functionality */
abstract class OxServiceBox[R] {
  private var used: Boolean          = false
  private val thread: Thread         = new Thread(() => threadMain())
  private val result: Promise[R]     = Promise[R]
  private val stopper: Promise[Unit] = Promise[Unit]
  private val stopped: Promise[Unit] = Promise[Unit]

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  /** Start the service, provides some information to be run. */
  def runService()(using Ox): R

  def run(): R = {
    if (used) {
      throw new IllegalStateException("Already started")
    }
    used = true
    thread.start()
    Await.result(result.future, Duration.Inf)
  }

  private def threadMain(): Unit = {
    val stopTime    = supervised {
      val serviceResult = Try(runService())
      result.tryComplete(serviceResult)
      // Wait until we should stop
      if (serviceResult.isSuccess) {
        Await.result(stopper.future, Duration.Inf)
      }
      System.currentTimeMillis()
    }
    val stoppedTime = System.currentTimeMillis()
    logger.debug(s"Stopping took ${stoppedTime - stopTime}ms")
    // We are done
    stopped.trySuccess(())
  }

  def stop(): Unit = {
    if (!used) {
      throw new IllegalStateException("Not started")
    }
    stopper.trySuccess(())
    Await.result(stopped.future, Duration.Inf)
  }
}

object OxServiceBox {

  /**
   * Wraps a simple function with OxService.
   * @return
   *   stop function and result
   */
  def run[R](f: Ox ?=> R): (() => Unit, R) = {
    val box                = new OxServiceBox[R] {
      override def runService()(using Ox): R = f
    }
    val result             = box.run()
    val stopFn: () => Unit = () => box.stop()
    (stopFn, result)
  }
}
