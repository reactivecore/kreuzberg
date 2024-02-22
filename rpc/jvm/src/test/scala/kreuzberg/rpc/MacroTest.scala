package kreuzberg.rpc

import scala.annotation.experimental
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

@experimental
class MacroTest extends TestBase {

  it should "work around" in {
    val dispatcher = Dispatcher.makeDispatcher(UserServiceDummy: UserService[Future])

    object Forwarder extends CallingBackend[Future] {

      override def call(service: String, name: String, input: Request): Future[Response] = {
        dispatcher.call(service, name, input)
      }
    }
    val stub = Stub.makeStub[UserService[Future]](Forwarder)
    Await.result(stub.logout("Foo", "bar"), Duration.Inf) shouldBe true
    Await.result(stub.authenticate(Credentials("Name", "Pass")), Duration.Inf) shouldBe AuthenticateResult(
      "123",
      Seq("admin")
    )
  }

}
