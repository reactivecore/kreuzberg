package kreuzberg.i18n

import kreuzberg.testcore.TestBase

class MessageFormatTest extends TestBase {

  it should "substitute placeholders" in {
    MessageFormat.format("Hello", Nil) shouldBe "Hello"
    MessageFormat.format("Hello %1, you have %2 messages", Seq("Alice", 3)) shouldBe "Hello Alice, you have 3 messages"
    MessageFormat.format("%2 before %1", Seq("a", "b")) shouldBe "b before a"
    MessageFormat.format("100%% of %1", Seq("x")) shouldBe "100% of x"
    MessageFormat.format("Missing: %2.", Seq("a")) shouldBe "Missing: ."
    MessageFormat.format("Not a placeholder: %a %", Nil) shouldBe "Not a placeholder: %a %"
  }

  it should "split templates into parts" in {
    import MessageFormat.Part.*
    MessageFormat.parts("") shouldBe Nil
    MessageFormat.parts("Hello") shouldBe Seq(Text("Hello"))
    MessageFormat.parts("See %1.") shouldBe Seq(Text("See "), Arg(1), Text("."))
    MessageFormat.parts("%2 before %1") shouldBe Seq(Arg(2), Text(" before "), Arg(1))
    MessageFormat.parts("%1%2") shouldBe Seq(Arg(1), Arg(2))
    MessageFormat.parts("100%% of %1") shouldBe Seq(Text("100% of "), Arg(1))
    MessageFormat.parts("Not a placeholder: %a %") shouldBe Seq(Text("Not a placeholder: %a %"))
  }
}
