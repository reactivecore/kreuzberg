package kreuzberg.rpc

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class DispatcherTest extends TestBase {

  @ApiName("myService")
  trait Service {
    def hello(): Future[Int]

    def world(a: String, b: Int): Future[Boolean]
  }

  class Dummy extends Service {
    var helloReturn: Future[Int]     = Future.successful(5)
    var gotA: String                 = ""
    var gotB: Int                    = 0
    var worldReturn: Future[Boolean] = Future.successful(false)

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
    val dummy      = new Dummy
    val dispatcher = Dispatcher.makeDispatcher[Service](dummy)
  }

  it should "decode requests" in new Env {
    await(dispatcher.call("myService", "hello", "{}")) shouldBe "5"
    await(dispatcher.call("myService", "world", """{"a": "a", "b": 3}""")) shouldBe "false"
    dummy.gotA shouldBe "a"
    dummy.gotB shouldBe 3
  }

  it should "handle errors" in new Env {
    awaitError[UnknownServiceError](dispatcher.call("Boom", "hello", "{}")).serviceName shouldBe "Boom"
    val c = awaitError[UnknownCallError](dispatcher.call("myService", "boom", "{}"))
    c.serviceName shouldBe "myService"
    c.call shouldBe "boom"

    awaitError[CodecError](dispatcher.call("myService", "hello", "illegal"))
    awaitError[CodecError](dispatcher.call("myService", "world", """{"a": "a", "b": true}"""))
  }

}
