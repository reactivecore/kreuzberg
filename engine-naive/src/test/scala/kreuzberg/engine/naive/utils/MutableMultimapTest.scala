package kreuzberg.engine.naive.utils

import kreuzberg.engine.naive.utils.MutableMultimap
import kreuzberg.testcore.TestBase

class MutableMultimapTest extends TestBase {

  it should "work" in {
    val map = new MutableMultimap[Int, String]()
    map.containsKey(5) shouldBe false
    map.add(5, "Hello")
    map.containsKey(5) shouldBe true
    map.add(5, "World")
    map.add(3, "Foo")
    {
      val collector = Seq.newBuilder[String]
      map.foreachKey(5)(collector.addOne)
      collector.result() shouldBe Seq("Hello", "World")
    }
    {
      val collector = Seq.newBuilder[String]
      map.foreachKey(6)(collector.addOne)
      collector.result() shouldBe empty
    }

    map.toSeq should contain theSameElementsAs Seq (5 -> "Hello", 5 -> "World", 3 -> "Foo")
    map.filterValuesInPlace(_ != "World")
    map.toSeq should contain theSameElementsAs Seq (5 -> "Hello", 3 -> "Foo")
    map.keys should contain theSameElementsAs Seq (5, 3)
    map.filterValuesInPlace(_ != "Foo")
    map.keys should contain theSameElementsAs Seq (5)
    map.toSeq should contain theSameElementsAs Seq (5 -> "Hello")
    map.isEmpty shouldBe false
    map.clear()
    map.isEmpty shouldBe true
  }
}
