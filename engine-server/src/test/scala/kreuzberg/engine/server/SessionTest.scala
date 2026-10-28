package kreuzberg.engine.server

import kreuzberg.*
import kreuzberg.extras.UrlResource
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.testcore.TestBase

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt

class SessionTest extends TestBase {

  private def withSession[T](component: Component)(f: Session => T): T = {
    val manager = SessionManager(component, () => ServiceRepository.empty, 1.hour)
    try f(manager.openPage(None, UrlResource("/"))(identity)._1)
    finally manager.close()
  }

  /** Process pending work, like the page's stream. */
  private def process(session: Session): Unit = session.withLock(session.settle()())

  private def timerComponent(ticks: AtomicInteger, timer: EventSource.Timer): Component = new SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = {
      addHandlerAny(timer)(ticks.incrementAndGet())
      div()
    }
  }

  "timers" should "run on the server and coalesce ticks" in {
    val ticks = AtomicInteger()
    withSession(timerComponent(ticks, EventSource.Timer(100.millis, periodic = true))) { session =>
      // Nobody processes in between: about 5 ticks happen, but only one waits
      Thread.sleep(550)
      process(session)
      ticks.get() should (be >= 1 and be <= 2)
      session.awaitActivity(1000)(session.changer.hasPending) shouldBe true
      process(session)
      ticks.get() should be >= 2
    }
  }

  it should "fire one-shot timers once" in {
    val ticks = AtomicInteger()
    withSession(timerComponent(ticks, EventSource.Timer(100.millis))) { session =>
      Thread.sleep(300)
      process(session)
      Thread.sleep(300)
      process(session)
      ticks.get() shouldBe 1
    }
  }

  it should "stop when the session closes" in {
    val ticks = AtomicInteger()
    withSession(timerComponent(ticks, EventSource.Timer(100.millis, periodic = true))) { session =>
      session.close()
      Thread.sleep(300)
      session.changer.hasPending shouldBe false
    }
  }

  "model values" should "be dropped when no component subscribes them anymore" in {
    val shown     = Model.create(true)
    val counter   = Model.create(0)
    val child     = new SimpleComponentBase {
      override def assemble(using sc: SimpleContext): Html = div(subscribe(counter).toString)
    }
    val component = new SimpleComponentBase {
      override def assemble(using sc: SimpleContext): Html = {
        if (subscribe(shown)) div(child.wrap) else div()
      }
    }
    withSession(component) { session =>
      def update(f: => Unit): Unit = session.withLock {
        session.run(f)
        session.settle()()
      }
      update(counter.set(5))
      session.modelValues.value(counter) shouldBe 5
      update(shown.set(false))
      session.modelValues.modelValues.keySet should not contain counter.id
      session.modelValues.value(counter) shouldBe 0
    }
  }
}
