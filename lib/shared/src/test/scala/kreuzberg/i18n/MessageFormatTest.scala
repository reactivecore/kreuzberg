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
}
