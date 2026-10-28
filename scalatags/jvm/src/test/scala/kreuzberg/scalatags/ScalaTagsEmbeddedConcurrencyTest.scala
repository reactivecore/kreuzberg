package kreuzberg.scalatags

import kreuzberg.*
import kreuzberg.scalatags.all.*
import kreuzberg.testcore.TestBase

import java.util.concurrent.{Callable, Executors, TimeUnit}
import scala.jdk.CollectionConverters.*

class ScalaTagsEmbeddedConcurrencyTest extends TestBase {
  case class DummyComponent(i: Int) extends ComponentBase {
    def assemble: Assembly = div(s"Hello ${i}")
  }

  "collectFrom" should "be thread safe" in {
    val executor = Executors.newFixedThreadPool(8)
    try {
      val tasks   = (0 until 2000).map { i =>
        val embedded                    = (0 until 5).map(j => DummyComponent(i * 10 + j).wrap)
        val callable: Callable[Boolean] = () => {
          ScalaTagsEmbedding.collectFrom(div(embedded)) == embedded.toVector
        }
        callable
      }
      val results = executor.invokeAll(tasks.asJava).asScala.map(_.get(30, TimeUnit.SECONDS))
      results.forall(identity) shouldBe true
    } finally {
      executor.shutdownNow()
    }
  }
}
