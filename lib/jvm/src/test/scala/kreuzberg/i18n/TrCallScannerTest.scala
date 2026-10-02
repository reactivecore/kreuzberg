package kreuzberg.i18n

import kreuzberg.testcore.TestBase

class TrCallScannerTest extends TestBase {

  def scan(content: String): Seq[(String, Int)] = TrCallScanner.scan(content).map(c => c.key -> c.argCount)

  it should "find simple calls" in {
    scan("""div(tr("index.title"), tr ( "index.clicked", count ))""") shouldBe Seq(
      "index.title"   -> 0,
      "index.clicked" -> 1
    )
  }

  it should "count top level arguments only" in {
    scan("""tr("a", foo(1, 2), Seq(3, 4), s"x, y", ',', { a, b })""") shouldBe Seq("a" -> 5)
    scan("""tr("a", (1, 2))""") shouldBe Seq("a" -> 1)
  }

  it should "find nested and multi line calls" in {
    val content =
      """val x = tr(
        |  "outer",
        |  tr("inner") // comment, with comma
        |)
        |""".stripMargin
    TrCallScanner.scan(content, "X.scala").map(c => (c.key, c.argCount, c.line)) shouldBe Seq(
      ("outer", 1, 1),
      ("inner", 0, 3)
    )
  }

  it should "ignore comments, strings and other identifiers" in {
    val content =
      """// tr("in.comment")
        |/* tr("in.block") */
        |val s = "tr(\"in.string\")"
        |val t = s""" + "\"\"\"" + """tr("in.triple")""" + "\"\"\"" + """
                                                                        |str("other"); attr("x"); my_tr("y")
                                                                        |inline def tr(inline key: String, args: Any*): String = ???
                                                                        |tr(someVariable)
                                                                        |val c = '"'; tr("after.char")
                                                                        |""".stripMargin
    scan(content) shouldBe Seq("after.char" -> 0)
  }

  it should "find qualified calls" in {
    scan("""kreuzberg.tr("q")""") shouldBe Seq("q" -> 0)
  }
}
