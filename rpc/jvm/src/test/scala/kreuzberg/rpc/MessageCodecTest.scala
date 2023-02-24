package kreuzberg.rpc

class MessageCodecTest extends TestBase {

  trait Env {
    val mc = MessageCodec.jsonArrayCodec
  }

  "Default JSON" should "encode/decode" in new Env {
    val sample  = Credentials("foo", "bar")
    val encoded = mc.encode(sample)
    encoded shouldBe """{"name":"foo","password":"bar"}"""
    val decoded = mc.decode[Credentials](encoded)
    decoded shouldBe Right(sample)
  }

  it should "handle multiple messages" in new Env {
    val encoded = mc.combine("foo" -> "\"a\"", "bar" -> "true")
    encoded shouldBe """["a",true]"""
    val decoded = mc.split(encoded, Seq("foo", "bar"))
    decoded shouldBe Right(Seq("\"a\"", "true"))
  }



}
