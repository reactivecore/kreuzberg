package kreuzberg.rpc

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class StubTest extends TestBase {

  trait SampleService {
    def hello(): Future[Int]

    def login(credentials: Credentials): Future[Boolean]

    def twoArgs(a1: Credentials, a2: Credentials): Future[String]
  }

  trait Env {
    object backend extends CallingBackend[Future, String] {
      val calls            = Seq.newBuilder[(String, String, String)]
      var response: String = ""

      def call(service: String, name: String, input: String): Future[String] = {
        calls += ((service, name, input))
        Future.successful(response)
      }
    }

    val service = Stub.makeStub[SampleService](backend)
  }

  it should "create a working stub" in new Env {
    backend.response = "10"

    await(service.hello()) shouldBe 10
    backend.calls.result() shouldBe Seq(("SampleService", "hello", "[]"))
    backend.calls.clear()
  }

  it should "encode multiple parameters" in new Env {
    backend.response = "\"ok\""

    await(service.twoArgs(Credentials("a", "b"), Credentials("c", "d"))) shouldBe "ok"
    backend.calls.result() shouldBe Seq(
      ("SampleService", "twoArgs", """[{"name":"a","password":"b"},{"name":"c","password":"d"}]""")
    )
  }

  it should "handle errors" in new Env {
    backend.response = "invalid"
    val r = service.login(Credentials("", ""))
    awaitError[CodecError](r)
  }

}
