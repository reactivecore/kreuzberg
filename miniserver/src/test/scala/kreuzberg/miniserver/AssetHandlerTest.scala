package kreuzberg.miniserver

import kreuzberg.testcore.TestBase

class AssetHandlerTest extends TestBase {

  "matchesETag" should "match equal ETags" in {
    AssetHandler.matchesETag("\"abc\"", "\"abc\"") shouldBe true
    AssetHandler.matchesETag("\"abd\"", "\"abc\"") shouldBe false
    AssetHandler.matchesETag("abc", "\"abc\"") shouldBe false
  }

  it should "match weak ETags" in {
    AssetHandler.matchesETag("W/\"abc\"", "\"abc\"") shouldBe true
  }

  it should "match lists and wildcards" in {
    AssetHandler.matchesETag("\"x\", W/\"abc\" ,\"y\"", "\"abc\"") shouldBe true
    AssetHandler.matchesETag("\"x\", \"y\"", "\"abc\"") shouldBe false
    AssetHandler.matchesETag("*", "\"abc\"") shouldBe true
  }
}
