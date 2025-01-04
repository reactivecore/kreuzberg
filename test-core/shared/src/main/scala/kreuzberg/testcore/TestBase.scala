package kreuzberg.testcore

import org.scalatest.{EitherValues, TryValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

abstract class TestBase extends AnyFlatSpec with Matchers with TryValues with EitherValues {

  def await[T](f: Future[T]): T = {
    Await.result(f, 1.minute)
  }

  def awaitError[E <: AnyRef](f: Future[?])(using ClassTag[E]): E = {
    intercept[E] {
      await(f)
    }
  }

}
