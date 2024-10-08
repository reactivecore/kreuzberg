package kreuzberg.rpc

import scala.annotation.experimental
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@experimental
class ContextualApiTest extends TestBase {

  case class MyContext(
      username: String
  )

  given p: ParamEncoder[MyContext] with ParamDecoder[MyContext] with {
    override def encode(name: String, value: MyContext, request: Request): Request = {
      val response = request.withHeader("username", value.username)
      response
    }

    override def decode(name: String, request: Request): MyContext = {
      val username = request.forceHeaderDecode("username")
      MyContext(username)
    }
  }

  trait Service {
    def hello()(using MyContext): Future[String]

    def helloDoubleArg(name: String, age: Int): Future[String]

    def tripleArgList(a: Int, b: Int)(c: String)(d: Double): Future[String]

    def helloDual(age: Int)(using MyContext): Future[String]

  }

  class Dummy extends Service {
    override def hello()(using c: MyContext) = Future.successful(
      s"Hello ${c.username}"
    )

    override def helloDoubleArg(name: String, age: Int): Future[String] = Future.successful {
      s"Hello ${name} with age ${age}"
    }

    def tripleArgList(a: Int, b: Int)(c: String)(d: Double): Future[String] = {
      Future.successful(
        s"${a}/${b}/${c}/${d}"
      )
    }

    override def helloDual(age: Int)(using c: MyContext): Future[String] = {
      Future.successful(s"Hello ${c.username}, are you ${age}?")
    }
  }

  trait Env {
    val dummy                          = new Dummy()
    val dispatcher: Dispatcher[Future] = Dispatcher.makeDispatcher[Service](dummy)
    val stub: Service                  = Stub.makeStub[Service](dispatcher.asCallingBackend)
  }

  it should "issue a simple call" in new Env {
    given c: MyContext = MyContext("Bob")
    await(stub.hello()) shouldBe "Hello Bob"
    await(stub.helloDoubleArg("Alice", 42)) shouldBe "Hello Alice with age 42"
    await(stub.helloDual(42)) shouldBe "Hello Bob, are you 42?"
  }
}
