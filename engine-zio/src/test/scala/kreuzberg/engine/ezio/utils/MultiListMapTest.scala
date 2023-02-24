package kreuzberg.engine.ezio.utils

import kreuzberg.engine.ezio.TestBase

class MultiListMapTest extends TestBase {
  it should "work" in {
    val a = MultiListMap[Int, String]()
    val added = a.add(2, "Hello").add(2, "World").add(3, "Foo")

    added.get(2) shouldBe List("World", "Hello")
    added.get(3) shouldBe List("Foo")
    added.get(4) shouldBe Nil

    added.remove(5) shouldBe (Nil -> added)
    added.remove(3) shouldBe List("Foo") -> MultiListMap(
      Map(
        2 -> List("World", "Hello")
      )
    )

    added.filterKeys(_ == 3) shouldBe MultiListMap(Map(3 -> List("Foo")))
    added.partitionKeys(_ == 2) shouldBe(MultiListMap(
      Map(2 -> List("World", "Hello"))
    ), MultiListMap(
      Map(3 -> List("Foo"))
    ))

    added.values.toSet shouldBe Set("Hello", "World", "Foo")
  }
}
