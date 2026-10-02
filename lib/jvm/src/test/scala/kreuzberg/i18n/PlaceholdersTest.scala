package kreuzberg.i18n

import kreuzberg.testcore.TestBase

class PlaceholdersTest extends TestBase {

  it should "find placeholders" in {
    Placeholders("Hello %1, %2 and %1") shouldBe Set(1, 2)
    Placeholders("100%% %%1") shouldBe Set.empty
    Placeholders.arity("%3 only") shouldBe 3
    Placeholders.arity("none") shouldBe 0
  }
}
