package kreuzberg.i18n

import kreuzberg.testcore.TestBase

class TrTest extends TestBase {

  it should "return the key without translations" in {
    tr("some.key") shouldBe "some.key"
    tr("some.key", 1, 2) shouldBe "some.key"
  }

  it should "not compile with a non literal key" in {
    assertDoesNotCompile("""val k = "x"; tr(k)""")
  }
}
