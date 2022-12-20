package kreuzberg.rpc

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.reflect.ClassTag

class TestBase extends AnyFlatSpec with Matchers {

  def await[T](f: Future[T]): T = {
    Await.result(f, 1.minute)
  }

  def awaitError[E <: AnyRef](f: Future[_])(using ClassTag[E]): E = {
    intercept[E] {
      await(f)
    }
  }

}
