package kreuzberg.rpc

class MessageCodecTest extends TestBase {

  trait Env {
    val mc = MessageCodec.jsonObjectCodec
  }

  it should "handle multiple messages" in new Env {
    val encoded = mc.combine("foo" -> "\"a\"", "bar" -> "true")
    encoded shouldBe """{"foo":"a","bar":true}"""
    val decoded = mc.split(encoded, Seq("foo", "bar"))
    decoded shouldBe Right(Seq("\"a\"", "true"))
  }

}
