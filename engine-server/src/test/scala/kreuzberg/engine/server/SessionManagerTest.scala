package kreuzberg.engine.server

import io.circe.{Json, JsonObject}
import kreuzberg.*
import kreuzberg.extras.UrlResource
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.testcore.TestBase

import scala.concurrent.duration.*

class SessionManagerTest extends TestBase {

  private object Root extends SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = div("hello")
  }

  private def withManager[T](expiry: FiniteDuration = 1.hour)(f: SessionManager => T): T = {
    val manager = SessionManager(Root, () => ServiceRepository.empty, expiry)
    try f(manager)
    finally manager.close()
  }

  private def open(manager: SessionManager, id: Option[String]): (Session, Boolean) = {
    manager.openPage(id, UrlResource("/"))(identity)
  }

  "openPage" should "create a session and assemble the tree" in withManager() { manager =>
    val (session, isNew) = open(manager, None)
    isNew shouldBe true
    session.tree.id shouldBe Root.id
    manager.get(session.id) shouldBe Some(session)
    manager.sessionCount shouldBe 1
  }

  it should "reuse a valid session and reset its inputs" in withManager() { manager =>
    val (first, _)      = open(manager, None)
    first.withLock { first.inputs = Map(Identifier(1) -> JsonObject("value" -> Json.fromString("old"))) }
    val (second, isNew) = open(manager, Some(first.id))
    isNew shouldBe false
    second shouldBe first
    second.inputs shouldBe empty
    manager.sessionCount shouldBe 1
  }

  it should "create a new session for unknown ids" in withManager() { manager =>
    val (session, isNew) = open(manager, Some("unknown"))
    isNew shouldBe true
    session.id should not be "unknown"
  }

  "get" should "not return expired sessions" in withManager(expiry = 1.milli) { manager =>
    val (session, _) = open(manager, None)
    Thread.sleep(10)
    manager.get(session.id) shouldBe empty
    manager.cleanup()
    manager.sessionCount shouldBe 0
  }
}
