package kreuzberg.rpc

import io.circe.Json

class MessageCodecTest extends TestBase {

  it should "handle multiple messages" in {
    val encoded = Json.obj(
      "foo" -> Json.fromInt(2),
      "bar" -> Json.fromBoolean(true)
    )

    MessageCodec.combine("foo" -> Json.fromInt(2), "bar" -> Json.fromBoolean(true)) shouldBe encoded
    MessageCodec.split(encoded, Seq("foo", "bar")) shouldBe Right(
      Seq(
        Json.fromInt(2),
        Json.fromBoolean(true)
      )
    )
  }

}
