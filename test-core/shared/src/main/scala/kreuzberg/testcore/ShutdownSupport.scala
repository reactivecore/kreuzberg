package kreuzberg.testcore

import org.scalatest.BeforeAndAfterEach

/** Registers a method to be called on shutdown. */
trait ShutdownSupport extends BeforeAndAfterEach {
  self: TestBase =>
  private var shutdowns: List[() => Unit] = Nil

  /** Registers the method. */
  def withShutdown(f: => Unit): Unit = {
    shutdowns = (() => f) :: shutdowns
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    shutdowns = Nil
  }

  override protected def afterEach(): Unit = {
    shutdowns.foreach(_())
    super.afterEach()
  }
}
