package kreuzberg.rpc

import io.circe.Json
import kreuzberg.testcore.TestBase

import scala.annotation.experimental
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@experimental
class StubTest extends TestBase {

  trait SampleService {
    def hello(): Future[Int]

    def login(credentials: Credentials): Future[Boolean]

    def twoArgs(a1: Credentials, a2: Credentials): Future[String]
  }

  trait Env {
    object backend extends CallingBackend[Future] {
      val calls              = Seq.newBuilder[(String, String, Request)]
      var response: Response = Response(Json.obj()) // scalafix:ok

      override def call(service: String, name: String, input: Request): Future[Response] = {
        calls += ((service, name, input))
        Future.successful(response)
      }
    }

    val service = Stub.makeStub[SampleService](backend)
  }

  it should "create a working stub" in new Env {
    backend.response = Response.forceJsonString("10")

    await(service.hello()) shouldBe 10
    backend.calls.result() shouldBe Seq(("SampleService", "hello", Request.forceJsonString("{}")))
    backend.calls.clear()
  }

  it should "encode multiple parameters" in new Env {
    backend.response = Response.forceJsonString("\"ok\"")

    await(service.twoArgs(Credentials("a", "b"), Credentials("c", "d"))) shouldBe "ok"
    backend.calls.result() shouldBe Seq(
      (
        "SampleService",
        "twoArgs",
        Request.forceJsonString("""{"a1":{"name":"a","password":"b"},"a2":{"name":"c","password":"d"}}""")
      )
    )
  }

  it should "handle errors" in new Env {
    backend.response = Response.forceJsonString("42")
    val r = service.login(Credentials("", ""))
    awaitError[CodecError](r)
  }

}
