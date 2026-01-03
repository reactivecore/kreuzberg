package kreuzberg.extras.tables

import kreuzberg.testcore.TestBase

class TabularTest extends TestBase {
  given stringCellRenderer: CellRenderer[String] = CellRenderer.default
  given intCellRenderer: CellRenderer[Int]       = CellRenderer.default

  case class Simple(
      name: String,
      age: Int
  )

  it should "derive for a simple type" in {
    val derived = Tabular.derived[Simple]
    derived.columns shouldBe Seq(
      TableColumn[String](
        name = "name",
        renderer = stringCellRenderer,
        ordering = Some(summon[Ordering[String]])
      ),
      TableColumn[Int](
        name = "age",
        renderer = intCellRenderer,
        ordering = Some(summon[Ordering[Int]])
      )
    )
  }

  val dummyOrdering: Ordering[String]    = Ordering.by(x => x.length)
  val starRenderer: CellRenderer[String] = stringCellRenderer.contraMap(s => "*" * s.length)

  case class Annotated(
      @UseTableColumn(orderingOverride = Some(dummyOrdering))
      name: String,
      @UseTableColumn[Int](columnName = "userId")
      user: Int,
      @UseTableColumn[String](
        columnName = "secret",
        hasOrdering = false,
        renderer = Some(starRenderer),
        thClassesOverride = "foo bar"
      )
      password: String
  )

  it should "derive for a complex type" in {
    val derived = Tabular.derived[Annotated]
    derived.columns shouldBe Seq(
      TableColumn[String](
        name = "name",
        renderer = stringCellRenderer,
        ordering = Some(dummyOrdering)
      ),
      TableColumn[Int](
        name = "userId",
        renderer = intCellRenderer,
        ordering = Some(summon[Ordering[Int]])
      ),
      TableColumn[String](
        name = "secret",
        renderer = starRenderer,
        ordering = None,
        thClassesOverride = "foo bar"
      )
    )
  }

  case class UnknownType(s: String)
  val unknownRenderer: CellRenderer[UnknownType] = stringCellRenderer.contraMap(_.s)
  given unknownOrdering: Ordering[UnknownType]   = Ordering.by(_.s)

  case class WithUnknown(
      @UseTableColumn("s", hasOrdering = false, renderer = Some(unknownRenderer))
      s: UnknownType
  )

  it should "derive from types without cell renderer" in {
    Tabular.derived[WithUnknown]
    // TODO: It would be better if we would not have to provide an ordering for the unknown type
    // but this is tricky to evaluate on compile time
  }
}
