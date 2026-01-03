package kreuzberg.rpc

import kreuzberg.testcore.TestBase

import scala.annotation.experimental
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@experimental
class DispatcherTest extends TestBase {

  @ApiName("myService")
  trait Service {
    def hello(): Future[Int]

    def world(a: String, b: Int): Future[Boolean]
  }

  class Dummy extends Service {
    var helloReturn: Future[Int]     = Future.successful(5)     // scalafix:ok
    var gotA: String                 = ""                       // scalafix:ok
    var gotB: Int                    = 0                        // scalafix:ok
    var worldReturn: Future[Boolean] = Future.successful(false) // scalafix:ok

    def hello(): Future[Int] = {
      helloReturn
    }

    def world(a: String, b: Int): Future[Boolean] = {
      gotA = a
      gotB = b
      worldReturn
    }
  }

  trait Env {
    val dummy                          = new Dummy
    val dispatcher: Dispatcher[Future] = Dispatcher.makeDispatcher[Service](dummy)
  }

  it should "decode requests" in new Env {
    await(dispatcher.call("myService", "hello", Request.forceJsonString("{}"))) shouldBe Response.forceJsonString("5")
    await(dispatcher.call("myService", "world", Request.forceJsonString("""{"a": "a", "b": 3}"""))) shouldBe Response
      .forceJsonString("false")
    dummy.gotA shouldBe "a"
    dummy.gotB shouldBe 3
  }

  it should "handle errors" in new Env {
    awaitError[UnknownServiceError](
      dispatcher.call("Boom", "hello", Request.forceJsonString("{}"))
    ).serviceName shouldBe "Boom"
    val c = awaitError[UnknownCallError](dispatcher.call("myService", "boom", Request.forceJsonString("{}")))
    c.serviceName shouldBe "myService"
    c.call shouldBe "boom"

    awaitError[CodecError](dispatcher.call("myService", "world", Request.forceJsonString("""{"a": "a", "b": true}""")))
  }

}
