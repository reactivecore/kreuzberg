package kreuzberg.rpc
import upickle.default.*

class FailureTest extends TestBase {

  val samples = Seq(
    CodecError("boom"),
    UnknownServiceError("service1"),
    UnknownCallError("service1", "call1"),
    ServiceExecutionError("message1", Some(42))
  )

  it should "serialize and deserialize all examples" in {
    for {
      sample <- samples
    } {
      val serialized = sample.encodeToJson
      val back       = Failure.decodeFromJson(serialized)
      back shouldBe sample
    }
  }
}
